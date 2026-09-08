// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.checks.DataFlowAnalyzer
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.skipParenthesizedExprDown

/**
 * Finds all references to [parameter] within [method], following reassignments to other variables
 * (e.g. `val other = parameter`) via lint's [DataFlowAnalyzer]. This lets callers track where a
 * parameter is actually used/escapes without relying on brittle name matching.
 */
internal fun findAllParameterReferences(parameter: UParameter, method: UMethod): Set<PsiElement> {
  val references = mutableSetOf<PsiElement>()
  parameter.sourcePsi?.let(references::add)
  method.accept(
    object : DataFlowAnalyzer(setOf(parameter)) {
      override fun receiver(call: UCallExpression) {
        (call.receiver?.skipParenthesizedExprDown()?.sourcePsi as? KtSimpleNameExpression)?.let {
          references += it
        }
      }

      override fun argument(call: UCallExpression, reference: UElement) {
        // If the parameter is an argument of the call (e.g. Modifier.then(modifier)), track that
        // call as if it returns this parameter so further reassignments are followed too.
        track(call)
        (reference.sourcePsi as? KtSimpleNameExpression)?.let { references += it }
      }
    }
  )
  return references
}

/** Finds names in this function that resolve to [declarations], excluding named-argument labels. */
internal fun KtNamedFunction.findReferencesTo(
  declarations: Collection<PsiElement>
): Set<KtNameReferenceExpression> {
  return buildSet {
    accept(
      object : KtTreeVisitorVoid() {
        override fun visitReferenceExpression(expression: KtReferenceExpression) {
          super.visitReferenceExpression(expression)
          if (expression !is KtNameReferenceExpression) return
          if (expression.parent is KtValueArgumentName) return
          if (expression.resolvesToAnyEquivalent(declarations)) {
            add(expression)
          }
        }
      }
    )
  }
}
