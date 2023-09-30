// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.StringOption
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.definedInInterface
import slack.lint.compose.util.emitsContent
import slack.lint.compose.util.isInternal
import slack.lint.compose.util.isOverride
import slack.lint.compose.util.isPreview
import slack.lint.compose.util.modifierParameter
import slack.lint.compose.util.returnsUnitOrVoid
import slack.lint.compose.util.sourceImplementation

class ModifierMissingDetector
@JvmOverloads
constructor(
  private val contentEmitterOption: ContentEmitterLintOption =
    ContentEmitterLintOption(CONTENT_EMITTER_OPTION)
) : ComposableFunctionDetector(contentEmitterOption), SourceCodeScanner {

  companion object {

    val CONTENT_EMITTER_OPTION = ContentEmitterLintOption.newOption()
    internal val VISIBILITY_THRESHOLD =
      StringOption(
        name = "visibility-threshold",
        description = "Visibility threshold to check for Modifiers",
        defaultValue = "only_public",
        explanation =
          "You can control the visibility of which composables to check for Modifiers. Possible values are: `only_public` (default), `public_and_internal` and `all`"
      )

    val ISSUE =
      Issue.create(
          id = "ComposeModifierMissing",
          briefDescription = "Missing modifier parameter",
          explanation =
            """
              This @Composable function emits content but doesn't have a modifier parameter.\
              See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information.
            """,
          category = Category.PRODUCTIVITY,
          priority = Priorities.NORMAL,
          severity = Severity.ERROR,
          implementation = sourceImplementation<ModifierMissingDetector>()
        )
        .setOptions(listOf(CONTENT_EMITTER_OPTION, VISIBILITY_THRESHOLD))
  }

  override fun visitComposable(context: JavaContext, function: KtFunction) {
    // We want to find all composable functions that:
    //  - emit content
    //  - are not overridden or part of an interface
    //  - are not a @Preview composable
    if (
        function.isOverride ||
        function.definedInInterface ||
        function.isPreview ||
        function.toUElementOfType<UMethod>()?.returnsUnitOrVoid(context.evaluator) == false
    ) {
      return
    }

    // We want to check now the visibility to see whether it's allowed by the configuration
    // Possible values:
    // - only_public: will check for modifiers only on public composables
    // - public_and_internal: will check for public and internal composables
    // - all: will check all composables (public, internal, protected, private
    val shouldCheck =
      when (VISIBILITY_THRESHOLD.getValue(context.configuration)) {
        "only_public" -> function.isPublic
        "public_and_internal" -> function.isPublic || function.isInternal
        "all" -> true
        else -> function.isPublic
      }
    if (!shouldCheck) return

    // If there is a modifier param, we bail
    if (function.modifierParameter != null) return

    // In case we didn't find any `modifier` parameters, we check if it emits content and report the
    // error if so.
    if (function.emitsContent(contentEmitterOption.value)) {
      context.report(
        ISSUE,
        function,
        context.getNameLocation(function),
        ISSUE.getExplanation(TextFormat.TEXT)
      )
    }
  }
}
