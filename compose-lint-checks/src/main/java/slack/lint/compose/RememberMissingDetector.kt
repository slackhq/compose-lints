// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.findChildrenByClass
import slack.lint.compose.util.sourceImplementation

class RememberMissingDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    private fun errorMessage(name: String): String =
      """
        Using `$name` in a @Composable function without it being inside of a remember function.
        If you don't remember the state instance, a new state instance will be created when the function is recomposed.
        See https://slackhq.github.io/compose-lints/rules/#state-should-be-remembered-in-composables for more information.
      """
        .trimIndent()

    private val MethodsThatNeedRemembering = setOf("derivedStateOf", "mutableStateOf")
    val DerivedStateOfNotRemembered = errorMessage("derivedStateOf")
    val MutableStateOfNotRemembered = errorMessage("mutableStateOf")

    val ISSUE =
      Issue.create(
        id = "ComposeRememberMissing",
        briefDescription = "State values should be remembered",
        explanation = "This is replaced when reported",
        category = Category.PRODUCTIVITY,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<RememberMissingDetector>()
      )
  }

  override fun visitComposable(context: JavaContext, function: KtFunction) {
    // To keep memory consumption in check, we first traverse down until we see one of our known
    // functions
    // that need remembering
    function
      .findChildrenByClass<KtCallExpression>()
      .filter { MethodsThatNeedRemembering.contains(it.calleeExpression?.text) }
      // Only for those, we traverse up to [function], to see if it was actually remembered
      .filterNot { it.isRemembered(function) }
      // If it wasn't, we show the error
      .forEach { callExpression ->
        when (callExpression.calleeExpression!!.text) {
          "mutableStateOf" -> {
            context.report(
              ISSUE,
              callExpression,
              context.getLocation(callExpression),
              MutableStateOfNotRemembered
            )
          }
          "derivedStateOf" -> {
            context.report(
              ISSUE,
              callExpression,
              context.getLocation(callExpression),
              DerivedStateOfNotRemembered
            )
          }
        }
      }
  }

  private fun KtCallExpression.isRemembered(stopAt: PsiElement): Boolean {
    var current: PsiElement = parent
    while (current != stopAt) {
      (current as? KtCallExpression)?.let { callExpression ->
        if (callExpression.calleeExpression?.text?.startsWith("remember") == true) return true
      }
      current = current.parent
    }
    return false
  }
}
