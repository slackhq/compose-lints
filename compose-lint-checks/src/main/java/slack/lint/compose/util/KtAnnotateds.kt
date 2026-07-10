// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

private const val COMPOSABLE = "androidx.compose.runtime.Composable"
private val COMPOSABLE_CLASS_ID = ClassId.topLevel(FqName(COMPOSABLE))

val UAnnotated.isComposable: Boolean
  get() = findAnnotation(COMPOSABLE) != null

val PsiMethod?.isComposableMethod: Boolean
  get() =
    this?.hasAnnotation(COMPOSABLE) == true ||
      this?.toUElementOfType<UMethod>()?.findAnnotation(COMPOSABLE) != null

val UCallExpression.isComposableCall: Boolean
  get() = resolve().isComposableMethod || resolvesToComposableCall()

private fun UCallExpression.resolvesToComposableCall(): Boolean {
  val call = sourcePsi as? KtCallExpression ?: return false
  return analyze(call) {
    call
      .resolveToCall()
      ?.successfulFunctionCallOrNull()
      ?.symbol
      ?.annotations
      ?.contains(COMPOSABLE_CLASS_ID) == true
  }
}
