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
import com.android.tools.lint.detector.api.isReceiver
import com.intellij.psi.util.firstLeaf
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.toUElementOfType
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.isComposable
import slack.lint.compose.util.isFunctionalInterface
import slack.lint.compose.util.isModifier
import slack.lint.compose.util.runIf
import slack.lint.compose.util.sourceImplementation

class ParameterOrderDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    fun createErrorMessage(currentOrder: List<UParameter>, properOrder: List<UParameter>): String =
      createErrorMessage(
        currentOrder.joinToString { getText(it) },
        properOrder.joinToString { getText(it) },
      )

    fun getText(uParameter: UParameter): String {
      return uParameter.sourcePsi?.text ?: "${uParameter.name}: ${uParameter.type.presentableText}"
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
    val currentOrder = method.uastParameters.filter { !it.isReceiver() }

    // We look in the original params without defaults and see if the last one is a function.
    val hasTrailingFunction = function.hasTrailingContentLambda(context.evaluator)
    val trailingLambda =
      if (hasTrailingFunction) {
        listOf(method.uastParameters.last())
      } else {
        emptyList()
      }

    // We extract the params without with and without defaults, and keep the order between them
    val (withDefaults, withoutDefaults) =
      currentOrder
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
      val valueParameterList = function.valueParameterList ?: return
      val errorLocation = context.getLocation(valueParameterList)
      context.report(
        ISSUE,
        function,
        errorLocation,
        createErrorMessage(currentOrder, properOrder),
        fix()
          .name("Reorder parameters")
          .replace()
          .with(buildNewText(valueParameterList, properOrder))
          .build(),
      )
    }
  }

  private fun buildNewText(
    valueParameterList: KtParameterList,
    parameters: List<UParameter>,
  ): String {
    return buildString {
      var parameterIndex = 0
      for (element in valueParameterList.firstLeaf().siblings()) {
        if (element is KtParameter) {
          append(parameters[parameterIndex++].sourcePsi!!.text)
        } else {
          append(element.text)
        }
      }
    }
  }

  // Checks if given function has a trailing lambda that can emit UI
  private fun KtFunction.hasTrailingContentLambda(evaluator: JavaEvaluator): Boolean {
    val lastParameter = valueParameters.lastOrNull()?.toUElementOfType<UParameter>() ?: return false
    val resolved = evaluator.getTypeClass(lastParameter.type) ?: return false
    if (!resolved.isFunctionalInterface) return false

    val functionType: KtFunctionType? =
      when (val outerType = valueParameters.lastOrNull()?.typeReference?.typeElement) {
        is KtFunctionType -> outerType
        is KtNullableType -> outerType.innerType as? KtFunctionType
        else -> null
      }
    // Ok if trailing lambda is composable
    if (lastParameter.type.isComposable) return true
    // Inline functions lambdas can emit content even if they aren't composable
    if (hasModifier(KtTokens.INLINE_KEYWORD)) return true
    // If type is aliased, type resolution may be incomplete without KaSession
    // (unstable in lint 8.7), so treat such parameters as valid trailing content lambdas
    if (functionType == null) return true
    // Lambdas with receivers (e.g., `LazyColumn`) are valid
    // even if they are not explicitly composable
    if (functionType.receiverTypeReference != null) return true
    // Any other trailing lambda is treated as a simple callback
    // and should not qualify as a content lambda
    return false
  }
}
