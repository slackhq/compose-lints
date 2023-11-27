// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.isKotlin
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import slack.lint.compose.util.*
import slack.lint.compose.util.sourceImplementation

class ModifierComposedDetector : Detector(), SourceCodeScanner {

  companion object {
    val ISSUE =
      Issue.create(
        id = "ComposeModifierComposed",
        briefDescription = "Don't use Modifier.composed {}",
        explanation =
          """
          Modifier.composed { ... } is no longer recommended due to performance issues. \
          You should use the Modifier.Node API instead, as it was designed from the ground up to be far more performant than composed modifiers.\
          See https://slackhq.github.io/compose-lints/rules/#avoid-modifier-extension-factory-functions for more information.
        """,
        category = Category.CORRECTNESS,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<ModifierComposedDetector>()
      )
  }

  override fun getApplicableUastTypes() =
    listOf<Class<out UElement>>(
      UCallExpression::class.java,
    )

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    if (!isKotlin(context.uastFile?.lang)) return null
    return object : UElementHandler() {
      override fun visitCallExpression(node: UCallExpression) {
        if (node.methodName != "composed") return
        context.evaluator.getTypeClass(node.receiverType)?.let { receiver ->
          if (context.evaluator.implementsInterface(receiver, "androidx.compose.ui.Modifier")) {
            context.report(
              ISSUE,
              node,
              context.getLocation(node),
              ISSUE.getExplanation(TextFormat.TEXT),
            )
          }
        }
      }
    }
  }
}
