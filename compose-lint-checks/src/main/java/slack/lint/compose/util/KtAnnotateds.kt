// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

internal const val COMPOSABLE_FQ_NAME = "androidx.compose.runtime.Composable"
internal val COMPOSABLE_CLASS_ID = ClassId.topLevel(FqName(COMPOSABLE_FQ_NAME))
internal const val READ_ONLY_COMPOSABLE_FQ_NAME = "androidx.compose.runtime.ReadOnlyComposable"
internal val READ_ONLY_COMPOSABLE_CLASS_ID = ClassId.topLevel(FqName(READ_ONLY_COMPOSABLE_FQ_NAME))

val UAnnotated.isComposable: Boolean
  get() = findAnnotation(COMPOSABLE_FQ_NAME) != null

val PsiMethod?.isComposableMethod: Boolean
  get() =
    this?.hasAnnotation(COMPOSABLE_FQ_NAME) == true ||
      this?.toUElementOfType<UMethod>()?.findAnnotation(COMPOSABLE_FQ_NAME) != null

val PsiMethod?.isReadOnlyComposableMethod: Boolean
  get() =
    this?.hasAnnotation(READ_ONLY_COMPOSABLE_FQ_NAME) == true ||
      this?.toUElementOfType<UMethod>()?.findAnnotation(READ_ONLY_COMPOSABLE_FQ_NAME) != null

val UCallExpression.isComposableCall: Boolean
  get() = composableCallKind() != ComposableCallKind.NONE

internal enum class ComposableCallKind {
  NONE,
  READ_ONLY,
  NON_READ_ONLY,
}

/** Classifies a declaration from its Compose annotations. */
internal fun composableCallKind(hasAnnotation: (ClassId) -> Boolean): ComposableCallKind {
  return when {
    !hasAnnotation(COMPOSABLE_CLASS_ID) -> ComposableCallKind.NONE
    hasAnnotation(READ_ONLY_COMPOSABLE_CLASS_ID) -> ComposableCallKind.READ_ONLY
    else -> ComposableCallKind.NON_READ_ONLY
  }
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
  return call.composableCallKind()
}

/** Resolves this Kotlin call and classifies its target's Compose annotations. */
internal fun KtCallExpression.composableCallKind(): ComposableCallKind {
  return analyze(this) {
    val annotations =
      this@composableCallKind.resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.annotations
        ?: return@analyze ComposableCallKind.NONE
    composableCallKind { it in annotations }
  }
}

/** Returns true when this resolves to a composable call that is not read-only. */
internal fun KtCallExpression.isNonReadOnlyComposableCall(): Boolean {
  return composableCallKind() == ComposableCallKind.NON_READ_ONLY
}

/** Returns true when this resolves to a non-read-only composable call that returns `Unit`. */
internal fun KtCallExpression.isNonReadOnlyUnitComposableCall(): Boolean {
  return analyze(this) {
    val functionCall = resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze false
    val returnType = functionCall.symbol.returnType.fullyExpandedType
    composableCallKind { it in functionCall.symbol.annotations } ==
      ComposableCallKind.NON_READ_ONLY &&
      !returnType.isMarkedNullable &&
      (returnType as? KaClassType)?.classId == StandardClassIds.Unit
  }
}

/** Returns true when this directly invokes a non-read-only composable function parameter. */
internal fun KtCallExpression.callsNonReadOnlyComposableFunctionParameter(): Boolean {
  val parameter = functionParameterCallTarget() ?: return false
  return analyze(parameter) {
    val functionType =
      parameter.symbol.returnType.fullyExpandedType as? KaFunctionType ?: return@analyze false
    functionType.isNonReadOnlyComposableFunctionType()
  }
}

/** Returns true when this function type is composable and not read-only. */
internal fun KaFunctionType.isNonReadOnlyComposableFunctionType(): Boolean {
  return composableCallKind { it in annotations } == ComposableCallKind.NON_READ_ONLY
}
