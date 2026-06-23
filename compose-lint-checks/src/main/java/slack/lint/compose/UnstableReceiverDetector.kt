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
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElementOfType
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.isStable
import slack.lint.compose.util.returnsUnitOrVoid
import slack.lint.compose.util.sourceImplementation

class UnstableReceiverDetector : ComposableFunctionDetector(), SourceCodeScanner {
  companion object {
    val ISSUE =
      Issue.create(
        id = "ComposeUnstableReceiver",
        briefDescription = "Unstable receivers will always be recomposed",
        explanation =
          issueText(
            """
            Instance composable functions on non-stable classes will always be recomposed. If
            possible, make the receiver type stable or refactor this function if that isn't
            possible.

            See https://slackhq.github.io/compose-lints/rules/#unstable-receivers for more information.
            """
          ),
        category = Category.PRODUCTIVITY,
        priority = Priorities.NORMAL,
        severity = Severity.WARNING,
        enabledByDefault = false,
        implementation = sourceImplementation<UnstableReceiverDetector>(),
      )
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    // Not implemented as we want properties too
  }

  override fun visitComposable(context: JavaContext, method: UMethod) {
    val evaluator = context.evaluator

    // Only Unit-returning functions can be skippable. Non-skippable functions will always be
    // recomposed regardless of the receiver type, so there's nothing actionable to report.
    if (!method.returnsUnitOrVoid(evaluator)) return

    val receiverTypeReference: KtTypeReference? =
      when (val source = method.sourcePsi) {
        is KtFunction -> source.receiverTypeReference
        is KtPropertyAccessor -> source.property.receiverTypeReference
        else -> null
      }

    if (receiverTypeReference != null) {
      // This is an extension. Its receiver is the value that gets recomposed, so check its
      // stability. The receiver is normally the first uastParameter, but for value-class receivers
      // K2 UAST doesn't surface it as a parameter, so fall back to resolving the type reference.
      val receiverType: PsiType? =
        method.uastParameters.firstOrNull()?.type
          ?: receiverTypeReference.toUElementOfType<UTypeReferenceExpression>()?.type
      if (receiverType?.isStable(evaluator, useSiteElement = receiverTypeReference) == false) {
        context.report(
          ISSUE,
          receiverTypeReference,
          context.getLocation(receiverTypeReference),
          ISSUE.getExplanation(TextFormat.TEXT),
        )
        return
      }
    }

    // For instance (and member-extension) functions the containing class is also passed as a
    // receiver, so check its stability too.
    if (!method.isStatic) {
      val containingClass = method.getContainingUClass()
      val containingClassType =
        containingClass
          // Only a real class declaration is passed as a receiver. This excludes objects (never
          // passed as a receiver) and file facades, whose synthetic *Kt class is the "container"
          // of top-level/extension declarations rather than an actual receiver type.
          ?.takeIf { it.sourcePsi is KtClass }
          ?.let(evaluator::getClassType) ?: return

      if (
        !containingClassType.isStable(
          evaluator,
          useSiteElement = containingClass.sourcePsi as KtClass,
          resolveUClass = { containingClass },
        )
      ) {
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
