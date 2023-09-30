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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtReferenceExpression
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.definedInInterface
import slack.lint.compose.util.findDirectChildrenByClass
import slack.lint.compose.util.isActual
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
          """
          Forwarding a `ViewModel` through multiple `@Composable` functions should be avoided. Consider using state hoisting.\
          See https://slackhq.github.io/compose-lints/rules/#hoist-all-the-things for more information.
        """,
        category = Category.CORRECTNESS,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<ViewModelForwardingDetector>()
      )
  }

  override fun visitComposable(context: JavaContext, function: KtFunction) {

    if (function.isOverride || function.definedInInterface || function.isActual) return
    val bodyBlock = function.bodyBlockExpression ?: return

    // We get here a list of variable names that tentatively contain ViewModels
    val parameters = function.valueParameterList?.parameters ?: emptyList()
    val viewModelParameterNames =
      parameters
        .filter { parameter ->
          // We can't do much better than this. We could look for viewModel() / weaverViewModel()
          // but that
          // would give us way less (and less useful) hits.
          parameter.typeReference?.text?.endsWith("ViewModel") ?: false
        }
        .mapNotNull { it.name }
        .toSet()

    // We want now to see if these parameter names are used in any other calls to functions that
    // start with a capital letter (so, most likely, composables).
    val forwardingCallExpressions =
      bodyBlock
        .findDirectChildrenByClass<KtCallExpression>()
        .filter { callExpression ->
          callExpression.calleeExpression?.unwrapParenthesis()?.text?.first()?.isUpperCase()
            ?: false
        }
        // Avoid LaunchedEffect/DisposableEffect/etc that can use the VM as a key
        .filterNot { callExpression -> callExpression.isRestartableEffect }
        .flatMap { callExpression ->
          // Get VALUE_ARGUMENT that has a REFERENCE_EXPRESSION. This would map to `viewModel` in
          // this example:
          // MyComposable(viewModel, ...)
          callExpression.valueArguments
            .mapNotNull { valueArgument ->
              valueArgument.getArgumentExpression() as? KtReferenceExpression
            }
            .filter { reference -> reference.text in viewModelParameterNames }
            .map { callExpression }
        }
    for (callExpression in forwardingCallExpressions) {
      context.report(
        ISSUE,
        callExpression,
        context.getLocation(callExpression),
        ISSUE.getExplanation(TextFormat.TEXT)
      )
    }
  }
}
