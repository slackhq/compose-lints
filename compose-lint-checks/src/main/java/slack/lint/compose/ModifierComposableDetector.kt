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
import slack.lint.compose.util.*
import slack.lint.compose.util.sourceImplementation

class ModifierComposableDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    val ISSUE =
      Issue.create(
        id = "ComposeComposableModifier",
        briefDescription = "Don't use @Composable builder functions for modifiers",
        explanation =
          """
          Using @Composable builder functions for modifiers is not recommended, as they cause unnecessary recompositions.\
          You should use the Modifier.Node API instead, as it limits recomposition to just the modifier instance, rather than the whole function tree.\
          See https://slackhq.github.io/compose-lints/rules/#avoid-modifier-extension-factory-functions for more information.
        """,
        category = Category.CORRECTNESS,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<ModifierComposableDetector>()
      )
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    if (!function.isModifierReceiver) return
    context.report(
      ISSUE,
      function,
      context.getLocation(function),
      ISSUE.getExplanation(TextFormat.TEXT),
    )
  }
}
