// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.toUElementOfType
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.isFunctionalInterface
import slack.lint.compose.util.isModifier
import slack.lint.compose.util.runIf
import slack.lint.compose.util.sourceImplementation

class ParameterOrderDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    fun createErrorMessage(
      currentOrder: List<UParameter>,
      properOrder: List<UParameter>
    ): String =
      createErrorMessage(
        currentOrder.joinToString { getText(it) },
        properOrder.joinToString { getText(it) },
      )

    fun getText(uParameter: UParameter): String =
      try {
        uParameter.text
      } catch (exception: Exception) {
        uParameter.name
      }

    private fun createErrorMessage(currentOrder: String, properOrder: String): String =
      """
        Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
        Current params are: [$currentOrder] but should be [$properOrder].
        See https://slackhq.github.io/compose-lints/rules/#ordering-composable-parameters-properly for more information.
      """
        .trimIndent()

    val ISSUE =
      Issue.create(
        id = "ComposeParameterOrder",
        briefDescription = "Composable function parameters should be ordered",
        explanation = "This is replaced when reported",
        category = Category.PRODUCTIVITY,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<ParameterOrderDetector>(),
      )
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    // We need to make sure the proper order is respected. It should be:
    // 1. params without defaults
    // 2. modifiers
    // 3. params with defaults
    // 4. optional: function that might have no default

    // Let's try to build the ideal ordering first, and compare against that.
    val currentOrder = method.uastParameters

    // We look in the original params without defaults and see if the last one is a function.
    val hasTrailingFunction = function.hasTrailingFunction(context.evaluator)
    val trailingLambda =
      if (hasTrailingFunction) {
        listOf(method.uastParameters.last())
      } else {
        emptyList()
      }

    // We extract the params without with and without defaults, and keep the order between them
    val (withDefaults, withoutDefaults) =
      method.uastParameters
        .runIf(hasTrailingFunction) { dropLast(1) }
        .partition { (it.sourcePsi as? KtParameter)?.hasDefaultValue() == true }

    // As ComposeModifierMissingCheck will catch modifiers without a Modifier default, we don't have
    // to care about that case. We will sort the params with defaults so that the modifier(s) go
    // first.
    val sortedWithDefaults =
      withDefaults.sortedWith(
        compareByDescending<UParameter> { it.isModifier(context.evaluator) }
          .thenByDescending { it.name == "modifier" }
      )

    // We create our ideal ordering of params for the ideal composable.
    val properOrder = withoutDefaults + sortedWithDefaults + trailingLambda

    // If it's not the same as the current order, we show the rule violation.
    if (currentOrder != properOrder) {
      val errorLocation = context.getLocation(function.valueParameterList)
      context.report(
        ISSUE,
        function,
        errorLocation,
        createErrorMessage(currentOrder, properOrder),
        fix()
          .replace()
          .range(errorLocation)
          .with(properOrder.joinToString(prefix = "(", postfix = ")") { getText(it) })
          .reformat(true)
          .build(),
      )
    }
  }

  private fun KtFunction.hasTrailingFunction(evaluator: JavaEvaluator): Boolean {
    val shallowCheck =
      when (val outerType = valueParameters.lastOrNull()?.typeReference?.typeElement) {
        is KtFunctionType -> true
        is KtNullableType -> outerType.innerType is KtFunctionType
        else -> false
      }
    if (shallowCheck) return true

    // Fall back to thorough check in case of aliases
    val resolved =
      evaluator.getTypeClass(
        valueParameters.lastOrNull()?.toUElementOfType<UParameter>()?.type
      )
        ?: return false
    return resolved.isFunctionalInterface
  }
}
