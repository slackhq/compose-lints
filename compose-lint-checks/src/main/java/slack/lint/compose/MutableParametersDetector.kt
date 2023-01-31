// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.kotlin.psi.KtFunction
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.isTypeMutable
import slack.lint.compose.util.sourceImplementation

class MutableParametersDetector : ComposableFunctionDetector(), SourceCodeScanner {
  companion object {
    val ISSUE =
      Issue.create(
        id = "ComposeMutableParameters",
        briefDescription = "Mutable objects in Compose will break state",
        explanation =
          """
              Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app.
              Mutable objects that are not observable, such as `ArrayList<T>` or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.
              See https://twitter.github.io/compose-rules/rules/#when-should-i-expose-modifier-parameters for more information.
            """,
        category = Category.PRODUCTIVITY,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<MutableParametersDetector>()
      )
  }

  override fun visitComposable(context: JavaContext, function: KtFunction) {
    function.valueParameters
      .filter { it.isTypeMutable }
      .forEach {
        context.report(
          ISSUE,
          function,
          context.getNameLocation(function),
          ISSUE.getExplanation(TextFormat.TEXT)
        )
      }
  }
}
