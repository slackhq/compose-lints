// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.definedInInterface
import slack.lint.compose.util.findAllParameterReferences
import slack.lint.compose.util.isActual
import slack.lint.compose.util.isComposableCall
import slack.lint.compose.util.isOverride
import slack.lint.compose.util.isRestartableEffect
import slack.lint.compose.util.sourceImplementation
import slack.lint.compose.util.unwrapParenthesis

class ViewModelForwardingDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    val ISSUE =
      Issue.create(
        id = "ComposeViewModelForwarding",
        briefDescription = "Don't forward ViewModels through composables",
        explanation =
          issueText(
            """
            Forwarding a `ViewModel` through multiple `@Composable` functions should be avoided.
            Consider using state hoisting.

            See https://slackhq.github.io/compose-lints/rules/#hoist-all-the-things for more information.
            """
          ),
        category = Category.CORRECTNESS,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<ViewModelForwardingDetector>(),
      )
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    if (function.isOverride || function.definedInInterface || function.isActual) return
    if (function.bodyBlockExpression == null) return

    // Identify the ViewModel parameters. We can't do much better than a type-name heuristic here:
    // looking for viewModel()/weaverViewModel() would give us way less (and less useful) hits.
    val viewModelParameters =
      method.uastParameters.filter { parameter ->
        parameter.sourcePsi is KtParameter &&
          context.evaluator.getTypeClass(parameter.type)?.name?.endsWith("ViewModel") == true
      }
    if (viewModelParameters.isEmpty()) return

    // Collect every reference to those parameters once - including reassignments (e.g.
    // `val vm = viewModel`) via data-flow analysis - then check each reference in place. This
    // avoids a second full walk of the body and only resolves calls that actually receive
    // a ViewModel.
    viewModelParameters
      .asSequence()
      .flatMap { findAllParameterReferences(it, method) }
      .mapNotNull { reference -> reference.forwardedComposableCall() }
      .distinct()
      .forEach { call ->
        context.report(
          ISSUE,
          call,
          context.getLocation(call),
          ISSUE.getExplanation(TextFormat.TEXT),
        )
      }
  }

  /**
   * If this reference is passed *whole* as an argument to a composable call (i.e., forwarding it),
   * returns that call. The reference must be the entire argument (so `Foo(viewModel)` matches but
   * `Foo(viewModel.state)` (hoisting a property) does not) and the call must look composable and
   * not be a restartable effect (which legitimately takes the ViewModel as a key).
   */
  private fun PsiElement.forwardedComposableCall(): KtCallExpression? {
    var parent = this.parent
    while (parent is KtParenthesizedExpression) {
      parent = parent.parent
    }
    val argument = parent as? KtValueArgument ?: return null
    val call = (argument.parent as? KtValueArgumentList)?.parent as? KtCallExpression ?: return null
    return call.takeIf { it.isLikelyComposableCall() && !it.isRestartableEffect }
  }

  /**
   * Whether this call is (probably) to a `@Composable` function. Composables are conventionally
   * capitalized; when the callee resolves we additionally require it to actually be `@Composable`
   * (which filters out non-composable calls such as event handlers), and when it can't be resolved
   * we fall back to the capitalization heuristic so the rule still works without the composables on
   * the analysis classpath.
   */
  private fun KtCallExpression.isLikelyComposableCall(): Boolean {
    val capitalized = calleeExpression?.unwrapParenthesis()?.text?.firstOrNull()?.isUpperCase()
    if (capitalized != true) return false
    val call = toUElementOfType<UCallExpression>() ?: return true
    if (call.resolve() == null) return true
    return call.isComposableCall
  }
}
