// Copyright (C) 2022 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity.IGNORE
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import slack.lint.compose.util.Priorities.NORMAL
import slack.lint.compose.util.sourceImplementation

internal class M2ApiDetector : Detector(), SourceCodeScanner {

  override fun getApplicableUastTypes() =
    listOf<Class<out UElement>>(
      UCallExpression::class.java,
      UImportStatement::class.java,
      UQualifiedReferenceExpression::class.java,
    )

  override fun createUastHandler(context: JavaContext) =
    object : UElementHandler() {
      override fun visitCallExpression(node: UCallExpression) = checkNode(node)

      override fun visitImportStatement(node: UImportStatement) = checkNode(node)

      override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) =
        checkNode(node)

      private fun checkNode(node: UResolvable) {
        val resolved = node.resolve() ?: return
        val packageName = context.evaluator.getPackage(resolved)?.qualifiedName ?: return
        if (packageName == M2Package) {
          context.report(
            issue = ISSUE,
            location = context.getLocation(node),
            message = ISSUE.getExplanation(TextFormat.TEXT),
          )
        }
      }
    }

  companion object {
    private const val M2Package = "androidx.compose.material"

    val ISSUE =
      Issue.create(
        id = "ComposeM2Api",
        briefDescription = "Using a Compose M2 API is not recommended",
        explanation =
          """
          Compose Material 2 (M2) is succeeded by Material 3 (M3). Please use M3 APIs.
          See https://slackhq.github.io/compose-lints/rules/#use-material-3 for more information.
        """,
        category = CORRECTNESS,
        priority = NORMAL,
        severity = IGNORE,
        implementation = sourceImplementation<M2ApiDetector>()
      )
  }
}
