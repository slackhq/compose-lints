// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.StringOption
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UMethod
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.StringSetLintOption
import slack.lint.compose.util.hasReceiverType
import slack.lint.compose.util.returnsUnitOrVoid
import slack.lint.compose.util.sourceImplementation

class ComposableFunctionNamingDetector
@JvmOverloads
constructor(
  private val allowedNames: StringSetLintOption =
    StringSetLintOption(ALLOWED_COMPOSABLE_FUNCTION_NAMES)
) : ComposableFunctionDetector(allowedNames) {

  companion object {
    internal val ALLOWED_COMPOSABLE_FUNCTION_NAMES =
      StringOption(
        "allowed-composable-function-names",
        "A comma-separated list of regexes of allowed composable function names",
        null,
        "This property should define comma-separated list of regexes of allowed composable function names.",
      )

    private val ISSUE_UPPERCASE =
      Issue.create(
          id = "ComposeNamingUppercase",
          briefDescription = "Unit Composables should be uppercase",
          explanation =
            """
              Composable functions that return Unit should start with an uppercase letter.\
              They are considered declarative entities that can be either present or absent in a composition and therefore follow the naming rules for classes.\
              See https://slackhq.github.io/compose-lints/rules/#naming-composable-functions-properly for more information.
            """,
          category = Category.PRODUCTIVITY,
          priority = Priorities.NORMAL,
          severity = Severity.ERROR,
          implementation = sourceImplementation<ComposableFunctionNamingDetector>(),
        )
        .setOptions(listOf(ALLOWED_COMPOSABLE_FUNCTION_NAMES))

    private val ISSUE_LOWERCASE =
      Issue.create(
          id = "ComposeNamingLowercase",
          briefDescription = "Value-returning Composables should be lowercase",
          explanation =
            """
              Composable functions that return a value should start with a lowercase letter.\
              While useful and accepted outside of @Composable functions, this factory function convention has drawbacks that set inappropriate expectations for callers when used with @Composable functions.\
              See https://slackhq.github.io/compose-lints/rules/#naming-composable-functions-properly for more information.
            """,
          category = Category.PRODUCTIVITY,
          priority = Priorities.NORMAL,
          severity = Severity.ERROR,
          implementation = sourceImplementation<ComposableFunctionNamingDetector>(),
        )
        .setOptions(listOf(ALLOWED_COMPOSABLE_FUNCTION_NAMES))

    val ISSUES = arrayOf(ISSUE_UPPERCASE, ISSUE_LOWERCASE)
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    // If it's a block we can't know if there is a return type or not from ktlint
    if (!function.hasBlockBody()) return
    val functionName = function.name?.takeUnless(String::isEmpty) ?: return
    val firstLetter = functionName.first()

    // If it's allowed, we don't report it
    val isAllowed = allowedNames.value.any { it.toRegex().matches(functionName) }
    if (isAllowed) return

    if (method.returnsUnitOrVoid(context.evaluator)) {
      // If it returns Unit or doesn't have a return type, we should start with an uppercase letter
      // If the composable has a receiver, we can ignore this.
      if (firstLetter.isLowerCase() && !function.hasReceiverType) {
        context.report(
          ISSUE_UPPERCASE,
          function,
          context.getNameLocation(function),
          ISSUE_UPPERCASE.getExplanation(TextFormat.TEXT),
        )
      }
    } else {
      // If it returns value, the composable should start with a lowercase letter
      if (firstLetter.isUpperCase()) {
        context.report(
          ISSUE_LOWERCASE,
          function,
          context.getNameLocation(function),
          ISSUE_LOWERCASE.getExplanation(TextFormat.TEXT),
        )
      }
    }
  }
}
