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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.kotlin.isKotlin
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
        "This property should define a comma-separated list of `CompositionLocal`s that should be allowed.",
      )

    private val ALLOW_LIST_ISSUE =
      Issue.create(
          id = "ComposeCompositionLocalUsage",
          briefDescription = "CompositionLocals are discouraged",
          explanation =
            """
              `CompositionLocal`s are implicit dependencies and creating new ones should be avoided. \
              See https://slackhq.github.io/compose-lints/rules/#compositionlocals for more information.
            """,
          category = Category.PRODUCTIVITY,
          priority = Priorities.NORMAL,
          severity = Severity.WARNING,
          implementation = sourceImplementation<CompositionLocalUsageDetector>(),
        )
        .setOptions(listOf(ALLOW_LIST))

    private val GETTER_ISSUE =
      Issue.create(
          id = "ComposeCompositionLocalGetter",
          briefDescription = "CompositionLocals should not use getters",
          explanation =
            """
              `CompositionLocal`s should be singletons and not use getters. Otherwise a new \
              instance will be returned every call.
            """,
          category = Category.PRODUCTIVITY,
          priority = Priorities.NORMAL,
          severity = Severity.ERROR,
          implementation = sourceImplementation<CompositionLocalUsageDetector>(),
        )
        .setOptions(listOf(ALLOW_LIST))

    val ISSUES = arrayOf(ALLOW_LIST_ISSUE, GETTER_ISSUE)

    /** Loads a comma-separated list of allowed names from the [ALLOW_LIST] option. */
    fun loadAllowList(context: Context): Set<String> {
      return context.configuration
        .getOption(ALLOW_LIST_ISSUE, ALLOW_LIST.name)
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

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UField::class.java, UMethod::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    if (!isKotlin(context.uastFile?.lang)) return null
    return object : UElementHandler() {
      override fun visitField(node: UField) {
        val ktProperty = node.sourcePsi as? KtProperty ?: return
        if (ktProperty.declaresCompositionLocal && ktProperty.nameIdentifier?.text !in allowList) {
          context.report(
            ALLOW_LIST_ISSUE,
            node,
            context.getLocation(node),
            ALLOW_LIST_ISSUE.getExplanation(TextFormat.TEXT),
          )
        }
      }

      override fun visitMethod(node: UMethod) {
        val source = node.sourcePsi
        if (source !is KtPropertyAccessor) return
        if (source.declaresCompositionLocal) {
          val reportable = source.node.findChildByType(KtTokens.GET_KEYWORD)?.psi ?: node
          context.report(
            GETTER_ISSUE,
            reportable,
            context.getLocation(reportable),
            GETTER_ISSUE.getExplanation(TextFormat.TEXT),
          )
        }
      }
    }
  }
}
