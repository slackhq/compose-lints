// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.unwrappedTargets
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression

internal data class DirectCall(val call: KtCallExpression, val receiver: KtExpression?)

/** Returns a direct call and its explicit receiver, while rejecting safe-qualified calls. */
internal fun KtExpression.directCall(): DirectCall? {
  val call = getPossiblyQualifiedCallExpression() ?: return null
  return when (this) {
    is KtCallExpression -> DirectCall(call, null)
    is KtDotQualifiedExpression -> DirectCall(call, receiverExpression)
    else -> null
  }
}

/** Returns true for unchanged parameters, constants, literal strings, and `this`. */
internal fun KtExpression.isForwardedValue(parameters: Set<KtParameter>): Boolean {
  return when (val expression = unwrapParenthesis()) {
    is KtConstantExpression -> true
    is KtNameReferenceExpression -> expression.resolvesToParameter(parameters)
    is KtStringTemplateExpression -> !expression.hasInterpolation()
    is KtThisExpression -> true
    else -> false
  }
}

/** Returns true when the receiver is `this`, a parameter, or a type or package qualifier. */
internal fun KtExpression.isForwardedReceiver(parameters: Set<KtParameter>): Boolean {
  return when (val expression = unwrapParenthesis()) {
    is KtThisExpression -> true
    is KtNameReferenceExpression ->
      expression.resolvesToParameter(parameters) || expression.resolvesToTypeOrPackage()
    is KtDotQualifiedExpression -> expression.isTypeOrPackageQualifier()
    else -> false
  }
}

/** Returns true when every segment resolves to a package or type-like declaration. */
internal fun KtExpression.isTypeOrPackageQualifier(): Boolean {
  return when (val expression = unwrapParenthesis()) {
    is KtNameReferenceExpression -> expression.resolvesToTypeOrPackage()
    is KtDotQualifiedExpression ->
      expression.receiverExpression.isTypeOrPackageQualifier() &&
        (expression.selectorExpression as? KtNameReferenceExpression)?.resolvesToTypeOrPackage() ==
          true
    else -> false
  }
}

/** Returns true when this reference resolves to one of [parameters]. */
internal fun KtNameReferenceExpression.resolvesToParameter(parameters: Set<KtParameter>): Boolean {
  return mainReference.unwrappedTargets.any(parameters::contains)
}

/** Returns true when this reference resolves to a package or type-like declaration. */
internal fun KtNameReferenceExpression.resolvesToTypeOrPackage(): Boolean {
  return mainReference.unwrappedTargets.any { target ->
    when (target) {
      is PsiClass,
      is PsiPackage,
      is KtClassOrObject,
      is KtTypeAlias -> true
      else -> false
    }
  }
}

/** Returns whether this call targets an inline function, or null when resolution fails. */
@OptIn(KaExperimentalApi::class)
internal fun KtCallExpression.callsInlineFunction(): Boolean? {
  return analyze(this) {
    (resolveToCall()?.successfulFunctionCallOrNull()?.symbol as? KaNamedFunctionSymbol)?.isInline
  }
}
