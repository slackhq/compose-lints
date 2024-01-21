// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElementOfType
import slack.lint.compose.util.PREVIEW_ANNOTATIONS
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.isPreview
import slack.lint.compose.util.sourceImplementation

class PreviewNamingDetector : Detector(), SourceCodeScanner {

  companion object {
    fun createMessage(count: Int, suggestedSuffix: String): String =
      """
        Preview annotations with $count preview annotations should end with the `$suggestedSuffix` suffix.
        See https://slackhq.github.io/compose-lints/rules/#naming-multipreview-annotations-properly for more information.
      """
        .trimIndent()

    val ISSUE =
      Issue.create(
        id = "ComposePreviewNaming",
        briefDescription = "Preview annotations require certain suffixes",
        explanation = "This is replaced when reporting",
        category = Category.PRODUCTIVITY,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<PreviewNamingDetector>()
      )
  }

  override fun getApplicableUastTypes() = listOf(UClass::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    if (!isKotlin(context.uastFile?.lang)) return null
    return object : UElementHandler() {
      override fun visitClass(node: UClass) {
        val clazz = node.sourcePsi as? KtClass ?: return
        if (!clazz.isAnnotation()) return
        if (!node.isPreview) return

        // We know here that we are in an annotation that either has a @Preview or other preview
        // annotations
        val count = node.uAnnotations.count {
          it.resolve().toUElementOfType<UClass>()?.let { annotation ->
            annotation.qualifiedName in PREVIEW_ANNOTATIONS ||
              annotation.uAnnotations.any { it.resolve()?.qualifiedName in PREVIEW_ANNOTATIONS }
          } ?: false
        }
        val name = clazz.nameAsSafeName.asString()
        val message =
          if (count == 1 && !name.endsWith("Preview")) {
            createMessage(count, "Preview")
          } else if (count > 1 && !name.endsWith("Previews")) {
            createMessage(count, "Previews")
          } else {
            null
          }
        message?.let {
          context.report(
            ISSUE,
            clazz,
            context.getLocation(clazz),
            it,
          )
        }
      }
    }
  }
}
