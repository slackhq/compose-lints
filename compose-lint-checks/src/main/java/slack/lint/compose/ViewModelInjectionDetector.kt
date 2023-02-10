// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.StringOption
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import slack.lint.compose.util.*
import slack.lint.compose.util.sourceImplementation

class ViewModelInjectionDetector
@JvmOverloads
constructor(private val allowedNames: StringSetLintOption = StringSetLintOption(ALLOW_LIST)) :
  ComposableFunctionDetector(allowedNames), SourceCodeScanner {

  companion object {

    internal val ALLOW_LIST =
      StringOption(
        "allowed-viewmodel-injection",
        "A comma-separated list of viewModel factories.",
        null,
        "This property should define comma-separated list of allowed viewModel factory names."
      )

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
        .setOptions(listOf(ALLOW_LIST))

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
          .filter {
            KnownViewModelFactories.contains(it.calleeExpression?.text) ||
              allowedNames.value.contains(it.calleeExpression?.text)
          }
          .map { property to it.calleeExpression!!.text }
      }
      .forEach { (property, viewModelFactoryName) ->
        context.report(
          ISSUE,
          property,
          context.getLocation(property),
          errorMessage(viewModelFactoryName),
          // TODO would be cool if we could auto-apply a fix like the detekt/ktlint version, but
          //  requires a rewrite.
        )
      }
  }
}
