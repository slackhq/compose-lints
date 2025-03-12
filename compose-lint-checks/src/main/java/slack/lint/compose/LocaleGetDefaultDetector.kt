// Copyright (C) 2025 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.isComposable
import slack.lint.compose.util.sourceImplementation

class LocaleGetDefaultDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    const val JAVA_UTIL_LOCALE = "java.util.Locale"

    val ISSUE =
      Issue.create(
        id = "LocaleGetDefaultDetector",
        briefDescription = "Avoid using `Locale.getDefault()` in Composable functions",
        explanation =
          """
                    Using `Locale.getDefault()` in a @Composable function does not trigger recomposition when the locale changes (e.g., during a Configuration change). \
                    Instead, use `LocalConfiguration.current.locales`, which correctly updates UI and works better for previews and tests.
                """,
        category = Category.CORRECTNESS,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<LocaleGetDefaultDetector>(),
      )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("getDefault")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {

    if (!context.evaluator.isMemberInClass(method, JAVA_UTIL_LOCALE)) return

    if (node.isInLambdaBlock()) return

    val parentFunction = node.getParentOfType(UMethod::class.java) ?: return
    if (parentFunction.isComposable) {
      context.report(
        ISSUE,
        node,
        context.getLocation(node),
        """
                        Don't use `Locale.getDefault()` in a @Composable function.
                        Use `LocalConfiguration.current.locales` instead to properly handle locale changes."
                     """,
      )
    }
  }

  private fun UCallExpression.isInLambdaBlock(): Boolean {
    return this.getParentOfType(ULambdaExpression::class.java) != null
  }
}
