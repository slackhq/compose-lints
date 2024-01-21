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
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.uast.UMethod
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.definedInInterface
import slack.lint.compose.util.isAbstract
import slack.lint.compose.util.isActual
import slack.lint.compose.util.isModifier
import slack.lint.compose.util.isOverride
import slack.lint.compose.util.lastChildLeafOrSelf
import slack.lint.compose.util.sourceImplementation

class ModifierWithoutDefaultDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    val ISSUE =
      Issue.create(
        id = "ComposeModifierWithoutDefault",
        briefDescription = "Missing Modifier default value",
        explanation =
          """
              This @Composable function has a modifier parameter but it doesn't have a default value.\
              See https://slackhq.github.io/compose-lints/rules/#modifiers-should-have-default-parameters for more information.
            """,
        category = Category.PRODUCTIVITY,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<ModifierWithoutDefaultDetector>(),
      )
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    if (
      function.definedInInterface || function.isActual || function.isOverride || function.isAbstract
    )
      return

    // Look for modifier params in the composable signature, and if any without a default value is
    // found, error out.
    method.uastParameters
      .withIndex()
      .filter { (_, param) -> param.isModifier(context.evaluator) }
      .filterNot { (_, param) ->
        param.sourcePsi is KtParameter && (param.sourcePsi as KtParameter).hasDefaultValue()
      }
      .forEach { (i, _) ->
        val modifierParameter = function.valueParameters[i]

        // This error is easily auto fixable, we just inject ` = Modifier` to the param
        val lastToken = modifierParameter.node.lastChildLeafOrSelf() as LeafPsiElement
        val currentText = lastToken.text
        context.report(
          ISSUE,
          modifierParameter,
          context.getLocation(modifierParameter),
          ISSUE.getExplanation(TextFormat.TEXT),
          fix()
            .replace()
            .name("Add '= Modifier' default value.")
            .range(context.getLocation(modifierParameter))
            .shortenNames()
            .text(currentText)
            .with("$currentText = Modifier")
            .autoFix()
            .build(),
        )
      }
  }
}
