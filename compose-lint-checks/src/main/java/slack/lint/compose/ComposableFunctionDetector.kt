// Copyright (C) 2023 Slack Technologies, LLC
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UMethod
import slack.lint.compose.util.LintOption
import slack.lint.compose.util.OptionLoadingDetector
import slack.lint.compose.util.isComposable

abstract class ComposableFunctionDetector(vararg options: LintOption) :
  OptionLoadingDetector(*options), SourceCodeScanner {

  final override fun getApplicableUastTypes() = listOf(UMethod::class.java)

  final override fun createUastHandler(context: JavaContext): UElementHandler? {
    if (!isKotlin(context.uastFile?.lang)) return null
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        val ktFunction = node.sourcePsi as? KtFunction ?: return
        if (ktFunction.isComposable) {
          visitComposable(context, ktFunction)
        }
      }
    }
  }

  abstract fun visitComposable(context: JavaContext, function: KtFunction)
}
