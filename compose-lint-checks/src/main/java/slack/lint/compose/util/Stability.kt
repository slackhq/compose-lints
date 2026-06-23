// Copyright (C) 2024 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.client.api.JavaEvaluator
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UTypeReferenceExpression
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
  useSiteElement: KtElement,
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
  val root = resolveUClass() ?: return false

  for (resolved in root.allSupertypes) {
    // Enums are stable
    if (resolved.isEnum) return true
    resolved.qualifiedName?.let { qualifiedName ->
      if (qualifiedName == "java.lang.String") return true
      if (qualifiedName in KnownStableConstructs.stableTypes) return true
    }
    if (resolved.isFunctionalInterface) return true
    val isStableAnnotated =
      resolved.annotations.any {
        // Is it itself a preview annotation?
        it.qualifiedName in STABILITY_ANNOTATIONS ||
          it.toUElementOfType<UAnnotation>()?.resolve()?.hasAnnotation(COMPOSE_STABLE_MARKER) ==
            true
      }
    if (isStableAnnotated) return true
  }

  // Value classes are stable when their underlying type is stable. UAST often erases a value class
  // to its underlying type, but this branch handles cases where the value class itself is still
  // visible, including compiled dependency classes.
  if (isValueClass(root, useSiteElement)) {
    // If we can't resolve the underlying type (e.g. a compiled class with no source), assume stable
    // since value classes overwhelmingly wrap stable primitives.
    return root.valueClassUnderlyingType?.isStable(evaluator, useSiteElement) ?: true
  }
  return false
}

@OptIn(KaExperimentalApi::class)
private fun PsiType.isValueClass(uClass: UClass, useSiteElement: KtElement): Boolean {
  return analyze(useSiteElement) {
    val useSiteSymbol =
      when (useSiteElement) {
        is KtClass -> useSiteElement.namedClassSymbol
        is KtTypeReference -> useSiteElement.type.expandedSymbol as? KaNamedClassSymbol
        else -> asKaType(useSiteElement)?.expandedSymbol as? KaNamedClassSymbol
      }
    useSiteSymbol?.isInline == true ||
      ((uClass.sourcePsi as? KtClass)?.namedClassSymbol)?.isInline == true ||
      (uClass.javaPsi.namedClassSymbol)?.isInline == true
  }
}

/** The type of a value class's single underlying property, if resolvable from source. */
private val UClass.valueClassUnderlyingType: PsiType?
  get() =
    (sourcePsi as? KtClass)
      ?.primaryConstructor
      ?.valueParameters
      ?.singleOrNull()
      ?.typeReference
      ?.toUElementOfType<UTypeReferenceExpression>()
      ?.type
