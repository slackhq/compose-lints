// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import slack.lint.compose.util.*
import slack.lint.compose.util.sourceImplementation

class ViewModelInjectionDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    private fun errorMessage(factoryName: String): String =
      """
        Implicit dependencies of composables should be made explicit.
        Usages of $factoryName to acquire a ViewModel should be done in composable default parameters, so that it is more testable and flexible.
        See https://slackhq.github.io/compose-lints/rules/#viewmodels for more information.
      """
        .trimIndent()

    val ISSUE =
      Issue.create(
        id = "ComposeViewModelInjection",
        briefDescription = "Implicit dependencies of composables should be made explicit",
        explanation = "Replaced when reporting",
        category = Category.CORRECTNESS,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<ViewModelInjectionDetector>()
      )

    private val KnownViewModelFactories by lazy {
      setOf(
        "viewModel", // AAC VM
        "weaverViewModel", // Weaver
        "hiltViewModel", // Hilt
        "injectedViewModel", // Whetstone
        "mavericksViewModel", // Mavericks
      )
    }
  }

  override fun visitComposable(context: JavaContext, function: KtFunction) {
    if (function.isOverride || function.definedInInterface) return

    val bodyBlock = function.bodyBlockExpression ?: return

    bodyBlock
      .findChildrenByClass<KtProperty>()
      .flatMap { property ->
        property
          .findDirectChildrenByClass<KtCallExpression>()
          .filter { KnownViewModelFactories.contains(it.calleeExpression?.text) }
          .map { property to it.calleeExpression!!.text }
      }
      .forEach { (property, viewModelFactoryName) ->
        context.report(
          ISSUE,
          property,
          context.getLocation(property),
          errorMessage(viewModelFactoryName),
          // TODO would be cool if we could auto-apply a fix like the detekt/ktlint version, but
          // requires a rewrite.
        )
      }
  }
}
