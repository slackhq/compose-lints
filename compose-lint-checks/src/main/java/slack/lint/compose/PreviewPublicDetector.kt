// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.BooleanOption
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.toUElementOfType
import slack.lint.compose.util.BooleanLintOption
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.isPreview
import slack.lint.compose.util.isPreviewParameter
import slack.lint.compose.util.sourceImplementation

class PreviewPublicDetector
@JvmOverloads
constructor(
  private val previewPublicOnlyIfParams: BooleanLintOption =
    BooleanLintOption(PREVIEW_PUBLIC_ONLY_IF_PARAMS_OPTION)
) : ComposableFunctionDetector(previewPublicOnlyIfParams), SourceCodeScanner {

  companion object {
    val PREVIEW_PUBLIC_ONLY_IF_PARAMS_OPTION =
      BooleanOption(
        "preview-public-only-if-params",
        "If set to true, this check will only enforce on previews that have no PreviewParameters",
        true,
        "If set to true, this check will only enforce on previews that have no PreviewParameters"
      )

    val ISSUE =
      Issue.create(
          id = "ComposePreviewPublic",
          briefDescription = "Preview composables should be private",
          explanation =
            """
              Composables annotated with `@Preview` that are used only for previewing the UI should not be public.\
              See https://slackhq.github.io/compose-lints/rules/#preview-composables-should-not-be-public for more information.
            """,
          category = Category.PRODUCTIVITY,
          priority = Priorities.NORMAL,
          severity = Severity.ERROR,
          implementation = sourceImplementation<PreviewPublicDetector>()
        )
        .setOptions(listOf(PREVIEW_PUBLIC_ONLY_IF_PARAMS_OPTION))
  }

  override fun visitComposable(context: JavaContext, function: KtFunction) {
    val method = function.toUElementOfType<UMethod>() ?: return
    // We only want previews
    if (!method.isPreview) return
    // We only care about public methods
    if (!function.isPublic) return

    // If the method is public, none of it's params should be tagged as preview
    // This is configurable by the `previewPublicOnlyIfParams` config value
    if (previewPublicOnlyIfParams.value) {
      if (function.valueParameters.none { it.toUElementOfType<UParameter>()?.isPreviewParameter == true }) return
    }

    // If we got here, it's a public method in a @Preview composable with a @PreviewParameter
    // parameter
    val visibility = function.visibilityModifierTypeOrDefault()
    val visibilityModifier = function.visibilityModifier()

    // If it has a visibility modifier, replace it
    // If it doesn't have one (i.e. implicit), put it before the "fun" keyword
    val fix =
      if (visibilityModifier != null) {
        val location = context.getLocation(visibilityModifier)
        fix()
          .replace()
          .name("Make 'private'")
          .range(location)
          .shortenNames()
          .text(visibility.value)
          .with(KtTokens.PRIVATE_KEYWORD.value)
          .autoFix()
          .build()
      } else if (function is KtNamedFunction) {
        val location = context.getLocation(function.funKeyword)
        fix()
          .replace()
          .name("Make 'private'")
          .range(location)
          .shortenNames()
          .text("fun")
          .with("${KtTokens.PRIVATE_KEYWORD.value} fun")
          .autoFix()
          .build()
      } else {
        null
      }

    context.report(
      ISSUE,
      function,
      context.getLocation(function),
      ISSUE.getExplanation(TextFormat.TEXT),
      fix
    )
  }
}
