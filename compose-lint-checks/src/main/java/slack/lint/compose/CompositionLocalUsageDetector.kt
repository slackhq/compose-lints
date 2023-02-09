// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.StringOption
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.isKotlin
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UField
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.declaresCompositionLocal
import slack.lint.compose.util.sourceImplementation

class CompositionLocalUsageDetector : Detector(), SourceCodeScanner {

  companion object {

    internal val ALLOW_LIST =
      StringOption(
        "allowed-composition-locals",
        "A comma-separated list of CompositionLocals that should be allowed",
        null,
        "This property should define a comma-separated list of `CompositionLocal`s that should be allowed."
      )

    val ISSUE =
      Issue.create(
          id = "ComposeCompositionLocalUsage",
          briefDescription = "CompositionLocals are discouraged",
          explanation =
            """
              `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.
              See https://slackhq.github.io/compose-lints/rules/#compositionlocals for more information.
            """,
          category = Category.PRODUCTIVITY,
          priority = Priorities.NORMAL,
          severity = Severity.ERROR,
          implementation = sourceImplementation<CompositionLocalUsageDetector>()
        )
        .setOptions(listOf(ALLOW_LIST))

    /** Loads a comma-separated list of allowed names from the [ALLOW_LIST] option. */
    fun loadAllowList(context: Context): Set<String> {
      return ALLOW_LIST.getValue(context.configuration)
        ?.splitToSequence(",")
        .orEmpty()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toSet()
    }
  }

  private lateinit var allowList: Set<String>

  override fun beforeCheckRootProject(context: Context) {
    super.beforeCheckRootProject(context)
    allowList = loadAllowList(context)
  }

  override fun getApplicableUastTypes() = listOf(UField::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    if (!isKotlin(context.uastFile?.lang)) return null
    return object : UElementHandler() {
      override fun visitField(node: UField) {
        val ktProperty = node.sourcePsi as? KtProperty ?: return
        if (ktProperty.declaresCompositionLocal && ktProperty.nameIdentifier?.text !in allowList) {
          context.report(
            ISSUE,
            node,
            context.getLocation(node),
            ISSUE.getExplanation(TextFormat.TEXT)
          )
        }
      }
    }
  }
}
