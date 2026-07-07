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
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.calleeIsComposable
import slack.lint.compose.util.hasComposableFunctionType
import slack.lint.compose.util.isComposable
import slack.lint.compose.util.slotParameters
import slack.lint.compose.util.sourceImplementation

/**
 * Reports `@Composable` functions and property getters whose body doesn't need a restart group. If
 * the body doesn't use the composition at all, the `@Composable` annotation can be removed. If the
 * body only reads `CompositionLocal.current`, the declaration can be annotated with
 * `@ReadOnlyComposable`.
 *
 * This detector only reports when the body and default argument values do not use composition.
 * Composable calls, non-read-only composable property reads, composable function values, and State
 * value access all count as composition usage. It also skips declarations whose annotation is part
 * of a contract, such as overrides, overridable members, interface members, and declarations with
 * composable slot parameters.
 */
class RedundantComposableDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    private const val COMPOSABLE = "androidx.compose.runtime.Composable"
    private const val COMPOSITION_LOCAL = "androidx.compose.runtime.CompositionLocal"
    private const val READ_ONLY_COMPOSABLE = "androidx.compose.runtime.ReadOnlyComposable"
    private const val STATE = "androidx.compose.runtime.State"

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

    val READ_ONLY_ISSUE =
      Issue.create(
        id = "ComposeReadOnlyComposable",
        briefDescription = "Composable only reads CompositionLocals",
        explanation =
          issueText(
            """
            This declaration only uses the composition to read `CompositionLocal` values, so it can
            be annotated with `@ReadOnlyComposable` to avoid generating a group around its body.

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

    val bodyUsage = body.compositionUsage(context)

    // A default value evaluated in the composable's context can also require the annotation.
    if (
      method.uastParameters.any {
        (it.uastInitializer?.compositionUsage(context) ?: CompositionUsage.NONE) !=
          CompositionUsage.NONE
      }
    ) {
      return
    }

    val annotation = method.uAnnotations.find { it.qualifiedName == COMPOSABLE }
    val location = annotation?.let(context::getLocation) ?: context.getNameLocation(method)
    when (bodyUsage) {
      CompositionUsage.NONE ->
        context.report(
          ISSUE,
          annotation ?: method,
          location,
          ISSUE.getExplanation(TextFormat.TEXT),
          annotation?.let { buildRemoveFix(context, it) },
        )
      CompositionUsage.READ_ONLY ->
        if (!method.hasAnnotation(READ_ONLY_COMPOSABLE)) {
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
      .with("${entry.text}\n${indentation}@$READ_ONLY_COMPOSABLE")
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
    val receiverClass = context.evaluator.getTypeClass(receiver.getExpressionType()) ?: return false
    return context.evaluator.implementsInterface(receiverClass, STATE, /* strict= */ false)
  }

  private fun PsiMethod?.isComposable(): Boolean =
    this?.toUElementOfType<UMethod>()?.isComposable == true

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

  /**
   * K2-based fallback that reads @Composable from Kotlin metadata via symbol analysis. This covers
   * binary dependencies where @Composable (AnnotationRetention.BINARY) may not be visible through
   * the PSI layer used by [PsiMethod.isComposable].
   */
  private fun UCallExpression.calleeIsComposableViaK2(): Boolean {
    val ktCall = sourcePsi as? KtCallExpression ?: return false
    return ktCall.calleeIsComposable()
  }

  private fun UCallExpression.compositionUsage(context: JavaContext): CompositionUsage {
    val method = resolve()
    return when {
      method.isCompositionLocalCurrent(context) -> CompositionUsage.READ_ONLY
      method.isComposable() || invokesComposableLambda() || calleeIsComposableViaK2() ->
        CompositionUsage.OTHER
      else -> CompositionUsage.NONE
    }
  }

  private fun USimpleNameReferenceExpression.compositionUsage(
    context: JavaContext
  ): CompositionUsage {
    val method = resolve() as? PsiMethod
    return when {
      method.isCompositionLocalCurrent(context, this) -> CompositionUsage.READ_ONLY
      method.isComposable() -> CompositionUsage.OTHER
      else -> CompositionUsage.NONE
    }
  }

  private fun UCallExpression.invokesComposableLambda(): Boolean {
    val callee = (sourcePsi as? KtCallExpression)?.calleeExpression ?: return false
    return callee.hasComposableFunctionType()
  }

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
