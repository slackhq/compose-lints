// Copyright (C) 2024 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.client.api.JavaEvaluator
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElementOfType

private const val COMPOSE_STABLE = "androidx.compose.runtime.Stable"
private const val COMPOSE_IMMUTABLE = "androidx.compose.runtime.Immutable"
private const val COMPOSE_STABLE_MARKER = "androidx.compose.runtime.StableMarker"

val STABILITY_ANNOTATIONS = setOf(COMPOSE_STABLE, COMPOSE_IMMUTABLE)

/**
 * Sets of known external stable constructs to the compose-compiler.
 *
 * @see <a
 *   href="https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/compiler/compiler-hosted/src/main/java/androidx/compose/compiler/plugins/kotlin/analysis/KnownStableConstructs.kt">KnownStableConstructs</a>
 */
object KnownStableConstructs {

  val stableTypes =
    setOf(
      Pair::class.qualifiedName!!,
      Triple::class.qualifiedName!!,
      Comparator::class.qualifiedName!!,
      Result::class.qualifiedName!!,
      ClosedRange::class.qualifiedName!!,
      ClosedFloatingPointRange::class.qualifiedName!!,
      // Guava
      "com.google.common.collect.ImmutableList",
      "com.google.common.collect.ImmutableEnumMap",
      "com.google.common.collect.ImmutableMap",
      "com.google.common.collect.ImmutableEnumSet",
      "com.google.common.collect.ImmutableSet",
      // Kotlinx immutable
      "kotlinx.collections.immutable.ImmutableCollection",
      "kotlinx.collections.immutable.ImmutableList",
      "kotlinx.collections.immutable.ImmutableSet",
      "kotlinx.collections.immutable.ImmutableMap",
      "kotlinx.collections.immutable.PersistentCollection",
      "kotlinx.collections.immutable.PersistentList",
      "kotlinx.collections.immutable.PersistentSet",
      "kotlinx.collections.immutable.PersistentMap",
      // Dagger
      "dagger.Lazy",
      // Coroutines
      "kotlin.coroutines.EmptyCoroutineContext",
    )
}

fun PsiType.isStable(
  evaluator: JavaEvaluator,
  resolveUClass: () -> UClass? = { evaluator.getTypeClass(this)?.toUElementOfType<UClass>() },
): Boolean {
  // Primitive types
  when (this) {
    PsiTypes.byteType(),
    PsiTypes.charType(),
    PsiTypes.doubleType(),
    PsiTypes.floatType(),
    PsiTypes.intType(),
    PsiTypes.longType(),
    PsiTypes.shortType(),
    PsiTypes.booleanType(),
    PsiTypes.voidType() -> return true
  }
  val resolved = resolveUClass() ?: return false

  // Enums are stable
  if (resolved.isEnum) return true
  resolved.qualifiedName?.let { qualifiedName ->
    if (qualifiedName == "java.lang.String") return true
    if (qualifiedName in KnownStableConstructs.stableTypes) return true
  }
  if (resolved.isFunctionalInterface) return true
  return resolved.uAnnotations.any {
    // Is it itself a preview annotation?
    it.resolve()?.let { cls ->
      cls.qualifiedName in STABILITY_ANNOTATIONS || cls.hasAnnotation(COMPOSE_STABLE_MARKER)
    } ?: false
  }
}
