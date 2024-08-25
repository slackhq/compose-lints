// Copyright (C) 2024 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.emitsContent
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
    val innerLambdas = composableBlockExpression.findChildrenByClass<KtLambdaExpression>().toList()

    slotParameters.forEach { slotParameter ->
      // Find lambdas that shadow this parameter name, to make sure that they aren't shadowing
      // the references we are looking through
      val lambdasWithMatchingParameterName =
        innerLambdas.filter { innerLambda ->
          // Otherwise look to see if any of the parameters on the inner lambda have the
          // same name
          innerLambda.valueParameters
            // Ignore parameters with a destructuring declaration instead of a named
            // parameter
            .filter { it.destructuringDeclaration == null }
            .any { it.name == slotParameter.name }
        }

      // Count all direct calls of the slot parameter.
      // NOTE: this misses cases where the slot parameter is passed as an argument to another
      // method, which may or may not invoke the slot parameter, but there are cases where that is
      // valid, like using the slot parameter as the key for a remember
      val slotParameterCallCount =
        callExpressions
          .filter { reference ->
            // The parameter is referenced if there is at least one reference that isn't shadowed by
            // an inner lambda
            lambdasWithMatchingParameterName.none { it.isAncestor(reference) }
          }
          .count { reference ->
            (reference.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ==
              slotParameter.name
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
