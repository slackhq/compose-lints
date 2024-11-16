// Copyright (C) 2024 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.asCall
import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypes
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.findChildrenByClass
import slack.lint.compose.util.isComposable
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

    slotParameters.forEach { slotParameter ->
      val slotElement: PsiElement? = slotParameter.sourceElement

      // Count all direct calls of the slot parameter.
      val slotParameterCallCount =
        callExpressions.count { callExpression ->
          val calleeElement: PsiElement? =
            callExpression.calleeExpression?.toUElement()?.tryResolve()

          calleeElement != null &&
            slotElement != null &&
            PsiEquivalenceUtil.areElementsEquivalent(calleeElement, slotElement)
        }

      // Count all instances where the slot parameter is passed to a slot argument for another
      // composable function. We make the assumption that any composable function that has a slot
      // and returns Unit will call the slot at least once (perhaps conditionally) because if it
      // didn't, what's the point of having the parameter?
      val slotParameterPassedAsSlotParameterCount =
        callExpressions.sumOf { callExpression ->
          val uCallExpression = callExpression.toUElement()?.asCall() ?: return@sumOf 0
          val psiMethod = uCallExpression.resolve() ?: return@sumOf 0

          val argumentMapping = context.evaluator.computeArgumentMapping(uCallExpression, psiMethod)

          argumentMapping.count { (expression, parameter) ->
            val argumentElement = expression.tryResolve()

            argumentElement != null &&
              slotElement != null &&
              // Called method is composable
              psiMethod.hasAnnotation("androidx.compose.runtime.Composable") &&
              // Called method returns Unit
              psiMethod.returnType?.isAssignableFrom(PsiTypes.voidType()) == true &&
              // Parameter is composable
              parameter.type.isComposable &&
              PsiEquivalenceUtil.areElementsEquivalent(argumentElement, slotElement)
          }
        }

      // Report an issue if the slot parameter was used in multiple places
      if (slotParameterCallCount + slotParameterPassedAsSlotParameterCount > 1) {
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
