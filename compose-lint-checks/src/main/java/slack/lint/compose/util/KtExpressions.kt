// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.components.evaluate
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.contracts.description.KaContractCallsInPlaceContractEffectDeclaration
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange
import org.jetbrains.kotlin.contracts.description.canBeVisited
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.unwrappedTargets
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression

internal data class DirectCall(val call: KtCallExpression, val receiver: KtExpression?)

private val BUILT_IN_FUNCTION_CLASS_NAME = Regex("(?:Suspend)?Function(?:\\d+|N)")
private val BUILT_IN_FUNCTION_PACKAGES =
  setOf(StandardClassIds.BASE_KOTLIN_PACKAGE, StandardClassIds.BASE_COROUTINES_PACKAGE)

/** Returns a direct call and its explicit receiver, while rejecting safe-qualified calls. */
internal fun KtExpression.directCall(): DirectCall? {
  val call = getPossiblyQualifiedCallExpression() ?: return null
  return when (this) {
    is KtCallExpression -> DirectCall(call, null)
    is KtDotQualifiedExpression -> DirectCall(call, receiverExpression)
    else -> null
  }
}

/** Returns a direct call and its explicit receiver, including safe-qualified calls. */
internal fun KtExpression.directCallIncludingSafe(): DirectCall? {
  directCall()?.let {
    return it
  }
  val expression = this as? KtSafeQualifiedExpression ?: return null
  val call = expression.selectorExpression as? KtCallExpression ?: return null
  return DirectCall(call, expression.receiverExpression)
}

/** Resolves `slot()`, `slot.invoke()`, or `slot?.invoke()` to its function parameter. */
internal fun KtCallExpression.functionParameterCallTarget(): KtParameter? {
  val callee = calleeExpression as? KtNameReferenceExpression ?: return null

  // A direct call such as `slot()` resolves the callee itself to the parameter.
  callee.mainReference.unwrappedTargets.filterIsInstance<KtParameter>().singleOrNull()?.let {
    return it
  }

  // For `slot.invoke()` and `slot?.invoke()`, resolve the receiver instead.
  if (callee.getReferencedName() != "invoke" || !callsBuiltInFunctionTypeInvoke()) return null

  val qualifiedCall = parent
  if (qualifiedCall !is KtDotQualifiedExpression && qualifiedCall !is KtSafeQualifiedExpression) {
    return null
  }
  if (qualifiedCall.selectorExpression !== this) return null
  val receiver =
    qualifiedCall.receiverExpression.unwrapParenthesis() as? KtNameReferenceExpression
      ?: return null
  return receiver.mainReference.unwrappedTargets.filterIsInstance<KtParameter>().singleOrNull()
}

/**
 * Returns how often this argument can run during [ownerCall], or null without a calls-in-place
 * contract. Constant `repeat` counts refine the contract's range.
 */
@OptIn(KaExperimentalApi::class)
internal fun KtExpression.callsInPlaceRange(ownerCall: KtCallExpression): EventOccurrencesRange? {
  return analyze(ownerCall) {
    val call = ownerCall.resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze null

    // Find the value parameter that receives this expression.
    val parameter =
      call.valueArgumentMapping.entries
        .firstOrNull { (argument, _) ->
          argument === this@callsInPlaceRange ||
            PsiEquivalenceUtil.areElementsEquivalent(argument, this@callsInPlaceRange) ||
            PsiTreeUtil.isAncestor(argument, this@callsInPlaceRange, false) ||
            PsiTreeUtil.isAncestor(this@callsInPlaceRange, argument, false)
        }
        ?.value
        ?.symbol ?: return@analyze null

    // Read that parameter's calls-in-place contract from the called function.
    val function = call.symbol as? KaNamedFunctionSymbol ?: return@analyze null
    val range =
      function.contractEffects
        .filterIsInstance<KaContractCallsInPlaceContractEffectDeclaration>()
        .firstOrNull { it.valueParameterReference.symbol == parameter }
        ?.occurrencesRange ?: return@analyze null

    if (function.callableId?.asSingleFqName()?.asString() != "kotlin.repeat") return@analyze range

    // repeat's contract does not connect its invocation range to the times argument.
    val countArgument =
      call.valueArgumentMapping.entries
        .singleOrNull { (_, mappedParameter) -> mappedParameter.symbol.name.asString() == "times" }
        ?.key
    val count =
      (countArgument?.evaluate() as? KaConstantValue.IntValue)?.value ?: return@analyze range
    when {
      count <= 0 -> EventOccurrencesRange.ZERO
      count == 1 -> EventOccurrencesRange.EXACTLY_ONCE
      else -> EventOccurrencesRange.MORE_THAN_ONCE
    }
  }
}

/** Returns the call that receives this lambda, including parenthesized arguments. */
internal fun KtLambdaExpression.ownerCall(): KtCallExpression? {
  var argument = parent
  while (argument is KtParenthesizedExpression) {
    argument = argument.parent
  }
  return when (argument) {
    is KtLambdaArgument -> argument.parent as? KtCallExpression
    is KtValueArgument -> argument.parent?.parent as? KtCallExpression
    else -> null
  }
}

/** Returns true when this labeled return targets [lambda]. */
internal fun KtReturnExpression.returnsTo(lambda: KtLambdaExpression): Boolean {
  return getTargetLabel()?.mainReference?.unwrappedTargets.orEmpty().any {
    PsiEquivalenceUtil.areElementsEquivalent(it, lambda) ||
      PsiEquivalenceUtil.areElementsEquivalent(it, lambda.functionLiteral)
  }
}

/** Returns true for a non-null `Nothing` return type. Unresolved calls return false. */
internal fun KtCallExpression.returnsNothing(): Boolean {
  return analyze(this) {
    val returnType =
      resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.returnType?.fullyExpandedType
        ?: return@analyze false
    !returnType.isMarkedNullable &&
      (returnType as? KaClassType)?.classId == StandardClassIds.Nothing
  }
}

/** Returns true if [ownerCall] can run this lambda before returning. */
internal fun KtLambdaExpression.isPossiblyExecutedBy(ownerCall: KtCallExpression): Boolean {
  val range = callsInPlaceRange(ownerCall)
  return when {
    range != null -> range.canBeVisited()
    ownerCall.composableCallKind() != ComposableCallKind.NONE -> true
    ownerCall.callsInlineFunction() == true -> true
    else -> false
  }
}

/** Returns true when this resolves to `invoke` on a built-in Kotlin function type. */
private fun KtCallExpression.callsBuiltInFunctionTypeInvoke(): Boolean {
  return analyze(this) {
    val callableId =
      resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.callableId ?: return@analyze false
    val classId = callableId.classId ?: return@analyze false
    callableId.callableName.asString() == "invoke" &&
      classId.packageFqName in BUILT_IN_FUNCTION_PACKAGES &&
      BUILT_IN_FUNCTION_CLASS_NAME.matches(classId.shortClassName.asString())
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

/** Returns true if this resolves to an element equivalent to one in [elements]. */
internal fun KtNameReferenceExpression.resolvesToAnyEquivalent(
  elements: Collection<PsiElement>
): Boolean {
  return mainReference.unwrappedTargets.any { target ->
    elements.any { PsiEquivalenceUtil.areElementsEquivalent(it, target) }
  }
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

/** Returns true if this calls an inline function, or null if resolution fails. */
@OptIn(KaExperimentalApi::class)
internal fun KtCallExpression.callsInlineFunction(): Boolean? {
  return analyze(this) {
    (resolveToCall()?.successfulFunctionCallOrNull()?.symbol as? KaNamedFunctionSymbol)?.isInline
  }
}
