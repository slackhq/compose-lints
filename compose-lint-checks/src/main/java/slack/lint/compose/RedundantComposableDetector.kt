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
import slack.lint.compose.util.hasComposableFunctionType
import slack.lint.compose.util.isComposable
import slack.lint.compose.util.slotParameters
import slack.lint.compose.util.sourceImplementation

/**
 * Reports `@Composable` functions and property getters that can be made non-composable.
 *
 * This detector only reports when the body and default argument values do not use composition.
 * Composable calls, composable property reads, composable function values, and State value access
 * all count as composition usage. It also skips declarations whose annotation is part of a
 * contract, such as overrides, overridable members, interface members, and declarations with
 * composable slot parameters.
 */
class RedundantComposableDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    private const val COMPOSABLE = "androidx.compose.runtime.Composable"
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
  }

  override fun visitComposable(context: JavaContext, method: UMethod) {
    // Need a body to analyze (skips abstract/expect/external declarations).
    val body = method.uastBody ?: return

    // Removing the annotation from these would break a contract or an override/implementation.
    if (method.isContractDeclaration()) return

    // Functions that take a @Composable lambda (a slot) generally invoke it, which requires the
    // composition. Even when they don't, skipping them avoids false positives.
    if (method.slotParameters().isNotEmpty()) return

    // If the body uses the composition in any way, the annotation is required.
    if (body.usesComposition(context)) return

    // A default value evaluated in the composable's context can also require the annotation.
    if (method.uastParameters.any { it.uastInitializer?.usesComposition(context) == true }) return

    val annotation = method.uAnnotations.find { it.qualifiedName == COMPOSABLE }
    val location = annotation?.let(context::getLocation) ?: context.getNameLocation(method)
    context.report(
      ISSUE,
      annotation ?: method,
      location,
      ISSUE.getExplanation(TextFormat.TEXT),
      annotation?.let { buildRemoveFix(context, it) },
    )
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

  private fun UElement.usesComposition(context: JavaContext): Boolean {
    var usesComposition = false
    accept(
      object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          return stopIf(node.usesComposition())
        }

        // Catches @Composable property reads, e.g. a CompositionLocal's `current`.
        override fun visitSimpleNameReferenceExpression(
          node: USimpleNameReferenceExpression
        ): Boolean {
          return stopIf(node.usesComposition())
        }

        // Catches reads and writes of a State's `value` (`state.value` / `state.value = ...`).
        override fun visitQualifiedReferenceExpression(
          node: UQualifiedReferenceExpression
        ): Boolean {
          return stopIf(node.isStateValueAccess(context))
        }

        private fun stopIf(foundCompositionUsage: Boolean): Boolean {
          usesComposition = usesComposition || foundCompositionUsage
          return usesComposition
        }
      }
    )
    return usesComposition
  }

  /** Whether [this] is an access (read or write) of a Compose [State]'s `value` property. */
  private fun UQualifiedReferenceExpression.isStateValueAccess(context: JavaContext): Boolean {
    if ((selector as? USimpleNameReferenceExpression)?.identifier != "value") return false
    val receiverClass = context.evaluator.getTypeClass(receiver.getExpressionType()) ?: return false
    return context.evaluator.implementsInterface(receiverClass, STATE, /* strict= */ false)
  }

  private fun PsiMethod?.isComposable(): Boolean =
    this?.toUElementOfType<UMethod>()?.isComposable == true

  private fun UCallExpression.usesComposition(): Boolean =
    resolve().isComposable() || invokesComposableLambda()

  private fun USimpleNameReferenceExpression.usesComposition(): Boolean =
    (resolve() as? PsiMethod).isComposable()

  private fun UCallExpression.invokesComposableLambda(): Boolean {
    val callee = (sourcePsi as? KtCallExpression)?.calleeExpression ?: return false
    return callee.hasComposableFunctionType()
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
