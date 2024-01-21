// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.client.api.JavaEvaluator
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.toUElementOfType

fun UParameter.isTypeMutable(evaluator: JavaEvaluator): Boolean {
  // Trivial check for Kotlin mutable collections. See its doc for details.
  // Note this doesn't work on typealiases, which unfortunately we can't really
  // do anything about
  if (
    (sourcePsi as? KtParameter)?.typeReference?.text?.matchesAnyOf(KnownMutableKotlinCollections) ==
      true
  ) {
    return true
  }

  val uParamClass = type.let(evaluator::getTypeClass)?.toUElementOfType<UClass>() ?: return false

  if (uParamClass.hasAnnotation("androidx.compose.runtime.Immutable")) {
    return false
  }

  return uParamClass.name in KnownMutableCommonTypesSimpleNames
}

/** Lint can't read "Mutable*" Kotlin collections that are compiler intrinsics. */
val KnownMutableKotlinCollections =
  sequenceOf(
      "MutableMap(\\s)?<.*,(\\s)?.*>\\??",
      "MutableSet(\\s)?<.*>\\??",
      "MutableList(\\s)?<.*>\\??",
      "MutableCollection(\\s)?<.*>\\??",
    )
    .map(::Regex)

val KnownMutableCommonTypesSimpleNames =
  setOf(
    // Set
    "MutableSet",
    "ArraySet",
    "HashSet",
    // List
    "MutableList",
    "ArrayList",
    // Array
    "SparseArray",
    "SparseArrayCompat",
    "LongSparseArray",
    "SparseBooleanArray",
    "SparseIntArray",
    // Map
    "MutableMap",
    "HashMap",
    "Hashtable",
    // Compose
    "MutableState",
    // Flow
    "MutableStateFlow",
    "MutableSharedFlow",
    // RxJava & RxRelay
    "PublishSubject",
    "BehaviorSubject",
    "ReplaySubject",
    "PublishRelay",
    "BehaviorRelay",
    "ReplayRelay"
  )

fun UParameter.isTypeUnstableCollection(evaluator: JavaEvaluator): Boolean {
  val uParamClass = type.let(evaluator::getTypeClass)?.toUElementOfType<UClass>() ?: return false

  if (uParamClass.hasAnnotation("androidx.compose.runtime.Immutable")) {
    return false
  }

  return uParamClass.qualifiedName in KnownUnstableCollectionTypes
}

val KnownUnstableCollectionTypes =
  sequenceOf("java.util.Collection", "java.util.Set", "java.util.List", "java.util.Map")
