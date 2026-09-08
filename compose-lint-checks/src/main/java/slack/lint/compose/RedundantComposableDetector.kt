// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.StringOption
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor
import slack.lint.compose.util.COMPOSABLE_CLASS_ID
import slack.lint.compose.util.COMPOSABLE_FQ_NAME
import slack.lint.compose.util.ComposableCallKind
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.READ_ONLY_COMPOSABLE_CLASS_ID
import slack.lint.compose.util.READ_ONLY_COMPOSABLE_FQ_NAME
import slack.lint.compose.util.StringSetLintOption
import slack.lint.compose.util.composableCallKind
import slack.lint.compose.util.hasComposableFunctionType
import slack.lint.compose.util.isComposableMethod
import slack.lint.compose.util.isReadOnlyComposableMethod
import slack.lint.compose.util.slotParameters
import slack.lint.compose.util.sourceImplementation
import slack.lint.compose.util.unwrapParenthesis

/**
 * Reports `@Composable` functions, property getters, and lambdas that don't need a restart group.
 * If a declaration doesn't use the composition at all, the `@Composable` annotation can be removed.
 * If its body and default argument values only call functions or read properties marked
 * `@ReadOnlyComposable`, the declaration can use that annotation too.
 *
 * Calls and property reads without `@ReadOnlyComposable`, composable function values, and State
 * value access prevent a read-only suggestion. It skips declarations whose annotation is part of a
 * contract, such as overrides, overridable members, interface members, and declarations with
 * composable slot parameters.
 */
class RedundantComposableDetector
@JvmOverloads
constructor(
  private val ignoredAnnotations: StringSetLintOption = StringSetLintOption(IGNORE_ANNOTATED)
) : ComposableFunctionDetector(ignoredAnnotations to ISSUE), SourceCodeScanner {

  override val includeComposableLambdas: Boolean
    get() = true

  companion object {
    private const val COMPOSITION_LOCAL = "androidx.compose.runtime.CompositionLocal"
    private const val STATE = "androidx.compose.runtime.State"

    internal val IGNORE_ANNOTATED =
      StringOption(
        "ignore-annotated",
        "A comma-separated list of fully qualified annotations on composables to ignore.",
        null,
        "Annotations on an object only ignore its Unit-returning composable operator invoke entry point.",
      )

    val ISSUE =
      Issue.create(
          id = "ComposeRedundantComposable",
          briefDescription = "Unnecessary @Composable annotation",
          explanation =
            issueText(
              """
            This declaration is annotated with `@Composable` but doesn't call any other
            `@Composable` functions or read any `@Composable` properties (like a
            `CompositionLocal`'s `current`), so it doesn't use the composition and the
            `@Composable` annotation can be removed.

            See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information.
            """
            ),
          category = Category.PRODUCTIVITY,
          priority = Priorities.NORMAL,
          severity = Severity.WARNING,
          implementation = sourceImplementation<RedundantComposableDetector>(),
        )
        .setOptions(listOf(IGNORE_ANNOTATED))

    val READ_ONLY_ISSUE =
      Issue.create(
        id = "ComposeReadOnlyComposable",
        briefDescription = "Composable can be @ReadOnlyComposable",
        explanation =
          issueText(
            """
            All composable functions and properties used by this declaration are marked
            `@ReadOnlyComposable`, so this declaration can be marked `@ReadOnlyComposable` too.

            See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information.
            """
          ),
        category = Category.PRODUCTIVITY,
        priority = Priorities.NORMAL,
        severity = Severity.INFORMATIONAL,
        implementation = sourceImplementation<RedundantComposableDetector>(),
      )

    val ISSUES = arrayOf(ISSUE, READ_ONLY_ISSUE)
  }

  override fun visitComposable(context: JavaContext, method: UMethod) {
    // Need a body to analyze (skips abstract/expect/external declarations).
    val body = method.uastBody ?: return

    // Removing the annotation from these would break a contract or an override/implementation.
    if (method.isContractDeclaration()) return

    // Functions that take a @Composable lambda (a slot) generally invoke it, which requires the
    // composition. Even when they don't, skipping them avoids false positives.
    if (method.slotParameters().isNotEmpty()) return

    val usage =
      method.uastParameters.fold(body.compositionUsage(context)) { result, parameter ->
        maxOf(
          result,
          parameter.uastInitializer?.compositionUsage(context) ?: CompositionUsage.NONE,
        )
      }

    val annotation = method.uAnnotations.find { it.qualifiedName == COMPOSABLE_FQ_NAME }
    val location = annotation?.let(context::getLocation) ?: context.getNameLocation(method)
    when (usage) {
      CompositionUsage.NONE ->
        if (!method.hasIgnoredAnnotation()) {
          context.report(
            ISSUE,
            annotation ?: method,
            location,
            ISSUE.getExplanation(TextFormat.TEXT),
            annotation?.let { buildRemoveFix(context, it) },
          )
        }
      CompositionUsage.READ_ONLY ->
        if (!method.hasAnnotation(READ_ONLY_COMPOSABLE_FQ_NAME)) {
          context.report(
            READ_ONLY_ISSUE,
            annotation ?: method,
            location,
            READ_ONLY_ISSUE.getExplanation(TextFormat.TEXT),
            annotation?.let { buildReadOnlyFix(context, it) },
          )
        }
      CompositionUsage.OTHER -> return
    }
  }

  override fun visitComposable(context: JavaContext, lambda: ULambdaExpression) {
    if (lambda.sourcePsi !is KtLambdaExpression) return
    val annotation = lambda.findAnnotation(COMPOSABLE_FQ_NAME) ?: return
    if (lambda.body.compositionUsage(context) != CompositionUsage.NONE) return
    if (ignoredAnnotations.value.any { lambda.findAnnotation(it) != null }) return

    context.report(
      ISSUE,
      annotation,
      context.getLocation(annotation),
      ISSUE.getExplanation(TextFormat.TEXT),
      buildRemoveFix(context, annotation),
    )
  }

  /** Whether this declaration is explicitly excluded from the redundant-composable issue. */
  private fun UMethod.hasIgnoredAnnotation(): Boolean {
    if (ignoredAnnotations.value.any { hasAnnotation(it) }) return true

    val containingClass = getContainingUClass() ?: return false // Only members have a container.
    return name == "invoke" && // Only match the object's invocation entry point.
      returnType == PsiTypes.voidType() && // Exclude Unit-returning functions only.
      // Require an actual operator, not just a function named invoke.
      (sourcePsi as? KtNamedFunction)?.hasModifier(KtTokens.OPERATOR_KEYWORD) == true &&
      containingClass.sourcePsi is KtObjectDeclaration && // Classes do not qualify.
      ignoredAnnotations.value.any { // The object itself must have an ignored annotation.
        containingClass.findAnnotation(it) != null
      }
  }

  /** Quickfix that removes the redundant `@Composable` annotation (and its trailing whitespace). */
  private fun buildRemoveFix(context: JavaContext, annotation: UAnnotation): LintFix? {
    val entry = annotation.sourcePsi ?: return null
    val contents = context.getContents() ?: return null
    // Delete by offset (not by matching text) so this is robust to the test framework's
    // fully-qualified / type-alias rewrites of the annotation.
    val start = entry.textRange.startOffset
    // Also consume the whitespace following the annotation so we don't leave a blank line or gap.
    // That whitespace is a sibling of the annotation when other modifiers follow, otherwise a
    // sibling of the enclosing modifier list.
    val trailingWhitespace = (entry.nextSibling ?: entry.parent?.nextSibling) as? PsiWhiteSpace
    val end = trailingWhitespace?.textRange?.endOffset ?: entry.textRange.endOffset
    return fix()
      .replace()
      .name("Remove redundant @Composable")
      .range(Location.create(context.file, contents, start, end))
      .with("")
      .autoFix()
      .build()
  }

  /** Quickfix that adds `@ReadOnlyComposable` next to the existing `@Composable` annotation. */
  private fun buildReadOnlyFix(context: JavaContext, annotation: UAnnotation): LintFix? {
    val entry = annotation.sourcePsi ?: return null
    val contents = context.getContents() ?: return null
    val start = entry.textRange.startOffset
    val location = Location.create(context.file, contents, start, entry.textRange.endOffset)
    val lineStart = contents.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }
    val indentation = contents.substring(lineStart, start).takeWhile(Char::isWhitespace)
    return fix()
      .replace()
      .name("Annotate with @ReadOnlyComposable")
      .range(location)
      .shortenNames()
      .text(entry.text)
      .with("${entry.text}\n${indentation}@$READ_ONLY_COMPOSABLE_FQ_NAME")
      .autoFix()
      .build()
  }

  /** Whether removing `@Composable` here would break inheritance or a platform contract. */
  private fun UMethod.isContractDeclaration(): Boolean {
    if (unwrapped?.isTopLevelKtOrJavaMember() == true) return false
    if (getContainingUClass()?.isInterface == true) return true
    // Modifiers live on the property for accessors, otherwise on the declaration itself.
    val modifierOwner =
      when (val source = sourcePsi) {
        is KtPropertyAccessor -> source.property
        is KtModifierListOwner -> source
        else -> null
      } ?: return false
    return MODALITY_MODIFIERS.any(modifierOwner::hasModifier)
  }

  private fun UElement.compositionUsage(context: JavaContext): CompositionUsage {
    var usage = CompositionUsage.NONE
    accept(
      object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          return stopIf(node.compositionUsage(context))
        }

        // Catches @Composable property reads, e.g. a CompositionLocal's `current`.
        override fun visitSimpleNameReferenceExpression(
          node: USimpleNameReferenceExpression
        ): Boolean {
          return stopIf(node.compositionUsage(context))
        }

        // Catches reads and writes of a State's `value` (`state.value` / `state.value = ...`).
        override fun visitQualifiedReferenceExpression(
          node: UQualifiedReferenceExpression
        ): Boolean {
          return stopIf(
            if (node.isStateValueAccess(context)) CompositionUsage.OTHER else CompositionUsage.NONE
          )
        }

        private fun stopIf(foundCompositionUsage: CompositionUsage): Boolean {
          if (foundCompositionUsage.ordinal > usage.ordinal) {
            usage = foundCompositionUsage
          }
          return usage == CompositionUsage.OTHER
        }
      }
    )
    return usage
  }

  /** Whether [this] is an access (read or write) of a Compose [State]'s `value` property. */
  private fun UQualifiedReferenceExpression.isStateValueAccess(context: JavaContext): Boolean {
    if ((selector as? USimpleNameReferenceExpression)?.identifier != "value") return false
    return receiver.getExpressionType().isComposeState(context)
  }

  private fun PsiType?.isComposeState(context: JavaContext): Boolean {
    val receiverClass = context.evaluator.getTypeClass(this) ?: return false
    return context.evaluator.implementsInterface(receiverClass, STATE, /* strict= */ false)
  }

  /**
   * Detect delegated reads without matching unrelated operators that happen to be named getValue.
   */
  private fun UCallExpression.isStateDelegateGetValue(context: JavaContext): Boolean {
    if (methodName != "getValue") return false
    return sequenceOf(receiver, *valueArguments.toTypedArray()).any {
      it?.getExpressionType().isComposeState(context)
    }
  }

  private fun USimpleNameReferenceExpression.isDelegatedStateRead(context: JavaContext): Boolean {
    val source = sourcePsi ?: return false
    val property = PsiTreeUtil.getParentOfType(source, KtProperty::class.java) ?: return false
    val delegateExpression = property.delegateExpression ?: return false
    return PsiTreeUtil.isAncestor(delegateExpression, source, /* strict= */ false) &&
      getExpressionType().isComposeState(context)
  }

  private fun PsiMethod?.isCompositionLocalCurrent(context: JavaContext): Boolean {
    if (this == null || name != "getCurrent") return false
    val containingClass = containingClass ?: return false
    return context.evaluator.implementsInterface(
      containingClass,
      COMPOSITION_LOCAL,
      /* strict= */ false,
    )
  }

  private fun PsiMethod?.isCompositionLocalCurrent(
    context: JavaContext,
    node: USimpleNameReferenceExpression,
  ): Boolean {
    if (node.identifier != "current") return false
    return isCompositionLocalCurrent(context)
  }

  private fun UCallExpression.compositionUsage(context: JavaContext): CompositionUsage {
    val method = resolve()
    if (method.isCompositionLocalCurrent(context)) return CompositionUsage.READ_ONLY
    return when (composableCallKind(method)) {
      ComposableCallKind.READ_ONLY -> CompositionUsage.READ_ONLY
      ComposableCallKind.NON_READ_ONLY -> CompositionUsage.OTHER
      ComposableCallKind.NONE ->
        if (invokesComposableLambda() || isStateDelegateGetValue(context)) {
          CompositionUsage.OTHER
        } else {
          CompositionUsage.NONE
        }
    }
  }

  private fun USimpleNameReferenceExpression.compositionUsage(
    context: JavaContext
  ): CompositionUsage {
    val method = resolve() as? PsiMethod
    return when {
      method.isCompositionLocalCurrent(context, this) -> CompositionUsage.READ_ONLY
      method.isComposableMethod && method.isReadOnlyComposableMethod -> CompositionUsage.READ_ONLY
      method.isComposableMethod || isDelegatedStateRead(context) -> CompositionUsage.OTHER
      else -> resolvesToComposablePropertyUsage()
    }
  }

  private fun USimpleNameReferenceExpression.resolvesToComposablePropertyUsage(): CompositionUsage {
    val reference = sourcePsi as? KtElement ?: return CompositionUsage.NONE
    return analyze(reference) {
      val access =
        reference.resolveToCall()?.singleVariableAccessCall()
          ?: return@analyze CompositionUsage.NONE
      if (access.kind !is KaVariableAccessCall.Kind.Read) {
        return@analyze CompositionUsage.NONE
      }
      val getter =
        (access.symbol as? KaPropertySymbol)?.getter ?: return@analyze CompositionUsage.NONE
      when {
        COMPOSABLE_CLASS_ID !in getter.annotations -> CompositionUsage.NONE
        READ_ONLY_COMPOSABLE_CLASS_ID in getter.annotations -> CompositionUsage.READ_ONLY
        else -> CompositionUsage.OTHER
      }
    }
  }

  private fun UCallExpression.invokesComposableLambda(): Boolean {
    val callee = (sourcePsi as? KtCallExpression)?.calleeExpression ?: return false
    return callee.hasComposableFunctionType() || callee.resolvesToInferredComposableLambda()
  }

  private fun KtExpression.resolvesToInferredComposableLambda(): Boolean {
    val property =
      referenceExpression()?.references?.firstOrNull()?.resolve() as? KtProperty ?: return false

    if (property.typeReference != null) return false

    val initializer =
      property.initializer?.unwrapParenthesis() as? KtAnnotatedExpression ?: return false

    return initializer.baseExpression is KtLambdaExpression &&
      initializer.annotationEntries.any {
        it.toUElementOfType<UAnnotation>()?.qualifiedName == COMPOSABLE_FQ_NAME
      }
  }

  /**
   * Entries are ordered from "no composition usage" to "usage that prevents a read-only
   * suggestion". [maxOf] relies on this order when combining usage from the body and default
   * arguments.
   */
  private enum class CompositionUsage {
    NONE,
    READ_ONLY,
    OTHER,
  }
}

private val MODALITY_MODIFIERS =
  listOf(
    KtTokens.OVERRIDE_KEYWORD,
    KtTokens.OPEN_KEYWORD,
    KtTokens.ABSTRACT_KEYWORD,
    KtTokens.EXPECT_KEYWORD,
    KtTokens.ACTUAL_KEYWORD,
    KtTokens.EXTERNAL_KEYWORD,
  )
