// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.returnsUnitOrVoid
import slack.lint.compose.util.isStable
import slack.lint.compose.util.sourceImplementation

class UnstableReceiverDetector : ComposableFunctionDetector(), SourceCodeScanner {
  companion object {
    val ISSUE =
      Issue.create(
        id = "ComposeUnstableReceiver",
        briefDescription = "Unstable receivers will always be recomposed",
        explanation =
          """
              Instance composable functions on non-stable classes will always be recomposed. \
              If possible, make the receiver type stable or refactor this function if that isn't possible. \
              See https://slackhq.github.io/compose-lints/rules/#unstable-receivers for more information.
            """,
        category = Category.PRODUCTIVITY,
        priority = Priorities.NORMAL,
        severity = Severity.WARNING,
        implementation = sourceImplementation<UnstableReceiverDetector>(),
      )
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    // Not implemented as we want properties too
  }

  override fun visitComposable(context: JavaContext, method: UMethod) {
    lateinit var nodeToReport: KtTypeReference
    val receiverParam = method.uastParameters.firstOrNull()
    val receiverType: PsiType? =
      when (val source = method.sourcePsi) {
        is KtFunction -> {
          source.receiverTypeReference?.let {
            nodeToReport = it
            // Receiver is the first uastParameter
            receiverParam?.type
          }
        }
        is KtPropertyAccessor -> {
          source.property.receiverTypeReference?.let {
            nodeToReport = it
            // Receiver is the first uastParameter
            receiverParam?.type
          }
        }
        else -> null
      }

    // Only non-Unit returning functions can be skippable.
    // Non-skippable functions will always be recomposed regardless of the receiver type.
    if (method.returnsUnitOrVoid(context.evaluator)) {
      if (receiverType?.isStable(context.evaluator) == false) {
        context.report(
          ISSUE,
          nodeToReport,
          context.getLocation(nodeToReport),
          ISSUE.getExplanation(TextFormat.TEXT),
        )
      } else if (!method.isTopLevelKtOrJavaMember() && !method.isStatic) {
        // We check both the receiver and the containing class, as classes could have
        // extension functions in their declarations too.
        val containingClass = method.getContainingUClass()
        val containingClassType =
          containingClass
            // If the containing class is an object, it will never be passed as a receiver arg
            ?.takeUnless { it.sourcePsi is KtObjectDeclaration }
            ?.let(context.evaluator::getClassType) ?: return

        if (!containingClassType.isStable(context.evaluator, resolveUClass = { containingClass })) {
          context.report(
            ISSUE,
            method,
            context.getNameLocation(method),
            ISSUE.getExplanation(TextFormat.TEXT),
          )
        }
      }
    }
  }
}
