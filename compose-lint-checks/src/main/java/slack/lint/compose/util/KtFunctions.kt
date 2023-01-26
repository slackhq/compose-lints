// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

val KtFunction.returnsValue: Boolean
  get() = typeReference != null && typeReference!!.text != "Unit"

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
