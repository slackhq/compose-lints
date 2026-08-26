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
private const val READ_ONLY_COMPOSABLE = "androidx.compose.runtime.ReadOnlyComposable"
private val READ_ONLY_COMPOSABLE_CLASS_ID = ClassId.topLevel(FqName(READ_ONLY_COMPOSABLE))

val UAnnotated.isComposable: Boolean
  get() = findAnnotation(COMPOSABLE) != null

val PsiMethod?.isComposableMethod: Boolean
  get() =
    this?.hasAnnotation(COMPOSABLE) == true ||
      this?.toUElementOfType<UMethod>()?.findAnnotation(COMPOSABLE) != null

val PsiMethod?.isReadOnlyComposableMethod: Boolean
  get() =
    this?.hasAnnotation(READ_ONLY_COMPOSABLE) == true ||
      this?.toUElementOfType<UMethod>()?.findAnnotation(READ_ONLY_COMPOSABLE) != null

val UCallExpression.isComposableCall: Boolean
  get() = composableCallKind() != ComposableCallKind.NONE

internal enum class ComposableCallKind {
  NONE,
  READ_ONLY,
  NON_READ_ONLY,
}

/** Classifies a call once, falling back to the Analysis API when UAST cannot resolve it. */
internal fun UCallExpression.composableCallKind(
  resolvedMethod: PsiMethod? = resolve()
): ComposableCallKind {
  if (resolvedMethod.isComposableMethod) {
    return if (resolvedMethod.isReadOnlyComposableMethod) {
      ComposableCallKind.READ_ONLY
    } else {
      ComposableCallKind.NON_READ_ONLY
    }
  }

  val call = sourcePsi as? KtCallExpression ?: return ComposableCallKind.NONE
  return analyze(call) {
    val annotations =
      call.resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.annotations
        ?: return@analyze ComposableCallKind.NONE
    when {
      COMPOSABLE_CLASS_ID !in annotations -> ComposableCallKind.NONE
      READ_ONLY_COMPOSABLE_CLASS_ID in annotations -> ComposableCallKind.READ_ONLY
      else -> ComposableCallKind.NON_READ_ONLY
    }
  }
}
