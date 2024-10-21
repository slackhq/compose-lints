// Copyright (C) 2024 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.findChildrenByClass
import slack.lint.compose.util.slotParameters
import slack.lint.compose.util.sourceImplementation

class SlotReusedDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {

    val ISSUE =
      Issue.create(
        id = "SlotReused",
        briefDescription = "Slots should be invoked in at most one place",
        explanation =
          """
            Slots should be invoked in at most once place to meet lifecycle expectations. \
            Slots should not be invoked in multiple places in source code, where the invoking location changes based on some condition. This will preserve the slot's internal state when the invoking location changes. \
            See https://slackhq.github.io/compose-lints/rules/#do-not-invoke-slots-in-more-than-once-place for more information.
          """,
        category = Category.CORRECTNESS,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<SlotReusedDetector>(),
      )
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    val composableBlockExpression = function.bodyBlockExpression ?: return
    val slotParameters = method.slotParameters(context.evaluator)

    val callExpressions = composableBlockExpression.findChildrenByClass<KtCallExpression>().toList()

    val psiManager = PsiManager.getInstance(context.project.ideaProject)

    slotParameters.forEach { slotParameter ->
      // Count all direct calls of the slot parameter.
      // NOTE: this misses cases where the slot parameter is passed as an argument to another
      // method, which may or may not invoke the slot parameter, but there are cases where that is
      // valid, like using the slot parameter as the key for a remember
      val slotParameterCallCount =
        callExpressions.count { callExpression ->
          psiManager.areElementsEquivalent(
            callExpression.calleeExpression?.toUElement()?.tryResolve(),
            slotParameter,
          )
        }

      // Report an issue if the slot parameter was invoked in multiple places
      if (slotParameterCallCount > 1) {
        context.report(
          ISSUE,
          slotParameter as UElement,
          context.getLocation(slotParameter as UElement),
          ISSUE.getExplanation(TextFormat.TEXT),
        )
      }
    }
  }
}
