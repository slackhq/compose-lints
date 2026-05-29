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
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UMethod
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
              Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app.\
              Mutable objects that are not observable, such as `ArrayList<T>` or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.\
              See https://slackhq.github.io/compose-lints/rules/#do-not-use-inherently-mutable-types-as-parameters for more information.
            """,
        category = Category.PRODUCTIVITY,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<MutableParametersDetector>(),
      )
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    method.uastParameters
      .filter { it.isTypeMutable(context.evaluator) }
      .forEach { parameter ->
        context.report(
          ISSUE,
          parameter.typeReference,
          context.getLocation(parameter.typeReference),
          ISSUE.getExplanation(TextFormat.TEXT),
        )
      }
  }
}
