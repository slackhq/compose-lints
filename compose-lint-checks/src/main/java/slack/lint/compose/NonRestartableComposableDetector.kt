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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType
import slack.lint.compose.util.COMPOSABLE_FQ_NAME
import slack.lint.compose.util.ComposableCallKind
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.READ_ONLY_COMPOSABLE_FQ_NAME
import slack.lint.compose.util.callsInlineFunction
import slack.lint.compose.util.composableCallKind
import slack.lint.compose.util.definedInInterface
import slack.lint.compose.util.directCall
import slack.lint.compose.util.findChildrenByClass
import slack.lint.compose.util.isForwardedReceiver
import slack.lint.compose.util.isForwardedValue
import slack.lint.compose.util.returnsUnitOrVoid
import slack.lint.compose.util.singleBodyExpression
import slack.lint.compose.util.sourceImplementation

class NonRestartableComposableDetector : ComposableFunctionDetector(), SourceCodeScanner {
  companion object {
    val ISSUE =
      Issue.create(
        id = "ComposeNonRestartableComposable",
        briefDescription = "Unnecessary composable restart boundary",
        explanation =
          issueText(
            """
            This composable's body only delegates to another composable. Consider marking it
            `@NonRestartableComposable` when its own restart and skip boundary is unlikely to be
            useful. The annotation avoids generating that code, but also prevents the wrapper from
            being restarted or skipped independently.

            See https://slackhq.github.io/compose-lints/rules/#avoid-unnecessary-restart-groups for more information.
            """
          ),
        category = Category.PERFORMANCE,
        priority = Priorities.LOW,
        severity = Severity.INFORMATIONAL,
        implementation = sourceImplementation<NonRestartableComposableDetector>(),
      )
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    val namedFunction = function as? KtNamedFunction ?: return
    if (!method.returnsUnitOrVoid(context.evaluator)) return
    if (namedFunction.isLocal() || namedFunction.definedInInterface) return
    if (NON_RESTARTABLE_MODIFIERS.any(namedFunction::hasModifier)) return
    if (INELIGIBLE_ANNOTATIONS.any(method::hasAnnotation)) return

    val bodyExpression = namedFunction.singleBodyExpression() ?: return
    // A pass-through body has one call and no lambda body of its own. This also rejects calls used
    // to calculate the receiver or arguments of the composable call.
    if (bodyExpression.findChildrenByClass<KtCallExpression>().count() != 1) return
    if (bodyExpression.findChildrenByClass<KtLambdaExpression>().any()) return

    val directCall = bodyExpression.directCall() ?: return
    val call = directCall.call
    val parameters = namedFunction.valueParameters.toSet()
    val hasNonForwardedArgument =
      call.valueArguments.any {
        it.getSpreadElement() != null ||
          it.getArgumentExpression()?.isForwardedValue(parameters) != true
      }
    if (hasNonForwardedArgument) return
    if (directCall.receiver?.isForwardedReceiver(parameters) == false) return

    val uCall = call.toUElementOfType<UCallExpression>() ?: return
    val resolvedCall = uCall.resolve() ?: return
    if (uCall.composableCallKind(resolvedCall) != ComposableCallKind.NON_READ_ONLY) return
    // A non-Unit composable cannot provide a restart boundary of its own.
    if (!resolvedCall.returnsUnitOrVoid(context.evaluator)) return
    // An inline call can put arbitrary work from the callee inside this function's boundary.
    if (call.callsInlineFunction() != false) return

    val annotation = method.findAnnotation(COMPOSABLE_FQ_NAME)
    context.report(
      ISSUE,
      annotation ?: method,
      annotation?.let(context::getLocation) ?: context.getNameLocation(method),
      ISSUE.getExplanation(TextFormat.TEXT),
      annotation?.let { buildAnnotationFix(context, it) },
    )
  }

  private fun buildAnnotationFix(context: JavaContext, annotation: UAnnotation): LintFix? {
    val entry = annotation.sourcePsi ?: return null
    val contents = context.getContents() ?: return null
    val start = entry.textRange.startOffset
    val location = Location.create(context.file, contents, start, entry.textRange.endOffset)
    val lineStart = contents.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }
    val indentation = contents.substring(lineStart, start).takeWhile(Char::isWhitespace)
    return fix()
      .replace()
      .name("Annotate with @NonRestartableComposable")
      .range(location)
      .shortenNames()
      .text(entry.text)
      .with("${entry.text}\n${indentation}@$NON_RESTARTABLE_COMPOSABLE")
      .build()
  }
}

private const val EXPLICIT_GROUPS_COMPOSABLE = "androidx.compose.runtime.ExplicitGroupsComposable"
private const val NON_RESTARTABLE_COMPOSABLE = "androidx.compose.runtime.NonRestartableComposable"
private const val NON_SKIPPABLE_COMPOSABLE = "androidx.compose.runtime.NonSkippableComposable"

private val NON_RESTARTABLE_MODIFIERS =
  listOf(
    KtTokens.INLINE_KEYWORD,
    KtTokens.OPEN_KEYWORD,
    KtTokens.OVERRIDE_KEYWORD,
    KtTokens.ABSTRACT_KEYWORD,
    KtTokens.EXPECT_KEYWORD,
    KtTokens.ACTUAL_KEYWORD,
    KtTokens.EXTERNAL_KEYWORD,
    KtTokens.TAILREC_KEYWORD,
  )

private val INELIGIBLE_ANNOTATIONS =
  listOf(
    EXPLICIT_GROUPS_COMPOSABLE,
    NON_RESTARTABLE_COMPOSABLE,
    NON_SKIPPABLE_COMPOSABLE,
    READ_ONLY_COMPOSABLE_FQ_NAME,
  )
