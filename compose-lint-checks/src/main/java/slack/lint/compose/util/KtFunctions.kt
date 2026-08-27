// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.client.api.JavaEvaluator
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.blockExpressionsOrSingle
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import org.jetbrains.uast.UMethod

fun UMethod.returnsUnitOrVoid(evaluator: JavaEvaluator): Boolean {
  return returnType.isUnitOrVoid(evaluator)
}

internal fun PsiMethod.returnsUnitOrVoid(evaluator: JavaEvaluator): Boolean {
  return returnType.isUnitOrVoid(evaluator)
}

private fun PsiType?.isUnitOrVoid(evaluator: JavaEvaluator): Boolean {
  val type = this ?: return false
  return type == PsiTypes.voidType() || evaluator.getTypeClass(type)?.qualifiedName == "kotlin.Unit"
}

internal fun KtNamedFunction.singleBodyExpression(): KtExpression? {
  val expression =
    bodyExpression?.blockExpressionsOrSingle()?.singleOrNull() as? KtExpression ?: return null
  return expression.unwrapParenthesis()
}

val KtFunction.hasReceiverType: Boolean
  get() = receiverTypeReference != null

val KtFunction.isPrivate: Boolean
  get() = visibilityModifierType() == KtTokens.PRIVATE_KEYWORD

val KtFunction.isProtected: Boolean
  get() = visibilityModifierType() == KtTokens.PROTECTED_KEYWORD

val KtFunction.isInternal: Boolean
  get() = visibilityModifierType() == KtTokens.INTERNAL_KEYWORD

val KtFunction.isOverride: Boolean
  get() = hasModifier(KtTokens.OVERRIDE_KEYWORD)

val KtFunction.isActual: Boolean
  get() = hasModifier(KtTokens.ACTUAL_KEYWORD)

val KtFunction.isExpect: Boolean
  get() = hasModifier(KtTokens.EXPECT_KEYWORD)

val KtFunction.isAbstract: Boolean
  get() = hasModifier(KtTokens.ABSTRACT_KEYWORD)

val KtFunction.definedInInterface: Boolean
  get() = ((parent as? KtClassBody)?.parent as? KtClass)?.isInterface() ?: false
