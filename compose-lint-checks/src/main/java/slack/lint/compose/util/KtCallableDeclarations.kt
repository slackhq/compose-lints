// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.client.api.JavaEvaluator
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtDeclarationWithReturnType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.toUElementOfType

/** Returns the class ID of the fully expanded return type, or null for other return types. */
internal fun KtDeclarationWithReturnType.expandedClassId(): ClassId? {
  return analyze(this) {
    (this@expandedClassId.returnType.fullyExpandedType as? KaClassType)?.classId
  }
}

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

  return uParamClass.qualifiedName in KnownMutableCommonTypes
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

val KnownMutableCommonTypes =
  sequenceOf(
      // java.util collections
      "java.util" to
        setOf(
          // Set
          "HashSet",
          "LinkedHashSet",
          "TreeSet",
          // List
          "ArrayList",
          "LinkedList",
          "Vector",
          "Stack",
          // Map
          "HashMap",
          "LinkedHashMap",
          "TreeMap",
          "Hashtable",
          "IdentityHashMap",
          "WeakHashMap",
          // Queue
          "ArrayDeque",
          "PriorityQueue",
        ),
      "java.util.concurrent" to
        setOf(
          "CopyOnWriteArraySet",
          "ConcurrentSkipListSet",
          "CopyOnWriteArrayList",
          "ConcurrentHashMap",
          "ConcurrentSkipListMap",
        ),
      "android.util" to
        setOf(
          "ArraySet",
          "SparseArray",
          "LongSparseArray",
          "SparseBooleanArray",
          "SparseIntArray",
        ),
      "androidx.collection" to
        setOf(
          "ArraySet",
          "MutableScatterSet",
          "MutableObjectList",
          "SparseArrayCompat",
          "MutableScatterMap",
          "MutableIntObjectMap",
          "MutableObjectIntMap",
          "MutableLongObjectMap",
          "MutableObjectLongMap",
          "LongSparseArray",
        ),
      "androidx.compose.runtime" to setOf("MutableState"),
      "kotlinx.coroutines.flow" to setOf("MutableStateFlow", "MutableSharedFlow"),
      "io.reactivex.subjects" to setOf("PublishSubject", "BehaviorSubject", "ReplaySubject"),
      "io.reactivex.rxjava3.subjects" to
        setOf("PublishSubject", "BehaviorSubject", "ReplaySubject"),
      "com.jakewharton.rxrelay2" to setOf("PublishRelay", "BehaviorRelay", "ReplayRelay"),
      "com.jakewharton.rxrelay3" to setOf("PublishRelay", "BehaviorRelay", "ReplayRelay"),
    )
    .flatMap { (packageName, simpleNames) -> simpleNames.map { "$packageName.$it" } }
    .toSet()

fun UParameter.isTypeUnstableCollection(evaluator: JavaEvaluator): Boolean {
  val uParamClass = type.let(evaluator::getTypeClass)?.toUElementOfType<UClass>() ?: return false

  if (uParamClass.hasAnnotation("androidx.compose.runtime.Immutable")) {
    return false
  }

  return uParamClass.qualifiedName in KnownUnstableCollectionTypes
}

val KnownUnstableCollectionTypes =
  sequenceOf("java.util.Collection", "java.util.Set", "java.util.List", "java.util.Map")
