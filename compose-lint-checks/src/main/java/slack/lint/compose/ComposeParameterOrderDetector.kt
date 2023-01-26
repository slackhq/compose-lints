// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtParameter
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.isModifier
import slack.lint.compose.util.runIf
import slack.lint.compose.util.sourceImplementation

class ComposeParameterOrderDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    fun createErrorMessage(
      currentOrder: List<KtParameter>,
      properOrder: List<KtParameter>
    ): String =
      createErrorMessage(
        currentOrder.joinToString { it.text },
        properOrder.joinToString { it.text }
      )

    fun createErrorMessage(currentOrder: String, properOrder: String): String =
      """
        Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
        Current params are: [$currentOrder] but should be [$properOrder].
        See https://twitter.github.io/compose-rules/rules/#ordering-composable-parameters-properly for more information.
      """
        .trimIndent()

    val ISSUE =
      Issue.create(
        id = "ComposeModifierWithoutDefault",
        briefDescription = "Composable function parameters should be ordered",
        explanation = "This is replaced when reported",
        category = Category.PRODUCTIVITY,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<ComposeParameterOrderDetector>()
      )
  }

  override fun visitComposable(context: JavaContext, function: KtFunction) {
    // We need to make sure the proper order is respected. It should be:
    // 1. params without defaults
    // 2. modifiers
    // 3. params with defaults
    // 4. optional: function that might have no default

    // Let's try to build the ideal ordering first, and compare against that.
    val currentOrder = function.valueParameters

    // We look in the original params without defaults and see if the last one is a function.
    val hasTrailingFunction = function.hasTrailingFunction
    val trailingLambda =
      if (hasTrailingFunction) {
        listOf(function.valueParameters.last())
      } else {
        emptyList()
      }

    // We extract the params without with and without defaults, and keep the order between them
    val (withDefaults, withoutDefaults) =
      function.valueParameters
        .runIf(hasTrailingFunction) { dropLast(1) }
        .partition { it.hasDefaultValue() }

    // As ComposeModifierMissingCheck will catch modifiers without a Modifier default, we don't have
    // to care
    // about that case. We will sort the params with defaults so that the modifier(s) go first.
    val sortedWithDefaults =
      withDefaults.sortedWith(
        compareByDescending<KtParameter> { it.isModifier }
          .thenByDescending { it.name == "modifier" }
      )

    // We create our ideal ordering of params for the ideal composable.
    val properOrder = withoutDefaults + sortedWithDefaults + trailingLambda

    // If it's not the same as the current order, we show the rule violation.
    if (currentOrder != properOrder) {
      context.report(
        ISSUE,
        function,
        context.getLocation(function),
        createErrorMessage(currentOrder, properOrder)
      )
    }
  }

  private val KtFunction.hasTrailingFunction: Boolean
    get() = valueParameters.lastOrNull()?.typeReference?.typeElement is KtFunctionType
}
