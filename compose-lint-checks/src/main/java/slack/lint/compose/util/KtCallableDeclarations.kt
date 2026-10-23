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

val KnownMutableCommonCollectionTypes =
  setOf(
    // Set
    "android.util.ArraySet",
    "androidx.collection.ArraySet",
    "androidx.collection.MutableScatterSet",
    "java.util.HashSet",
    "java.util.LinkedHashSet",
    "java.util.TreeSet",
    "java.util.concurrent.ConcurrentSkipListSet",
    "java.util.concurrent.CopyOnWriteArraySet",
    "kotlin.collections.HashSet",
    "kotlin.collections.LinkedHashSet",
    // List
    "androidx.collection.MutableObjectList",
    "java.util.ArrayList",
    "java.util.LinkedList",
    "java.util.Stack",
    "java.util.Vector",
    "java.util.concurrent.CopyOnWriteArrayList",
    "kotlin.collections.ArrayList",
    // Array
    "android.util.LongSparseArray",
    "android.util.SparseArray",
    "android.util.SparseBooleanArray",
    "android.util.SparseIntArray",
    "androidx.collection.LongSparseArray",
    "androidx.collection.SparseArrayCompat",
    // Map
    "androidx.collection.MutableIntObjectMap",
    "androidx.collection.MutableLongObjectMap",
    "androidx.collection.MutableObjectIntMap",
    "androidx.collection.MutableObjectLongMap",
    "androidx.collection.MutableScatterMap",
    "java.util.HashMap",
    "java.util.Hashtable",
    "java.util.IdentityHashMap",
    "java.util.LinkedHashMap",
    "java.util.TreeMap",
    "java.util.WeakHashMap",
    "java.util.concurrent.ConcurrentHashMap",
    "java.util.concurrent.ConcurrentSkipListMap",
    "kotlin.collections.HashMap",
    "kotlin.collections.LinkedHashMap",
    // Queue
    "java.util.ArrayDeque",
    "java.util.PriorityQueue",
    "kotlin.collections.ArrayDeque",
  )

val KnownMutableCommonTypes =
  KnownMutableCommonCollectionTypes +
    setOf(
      // Compose
      "androidx.compose.runtime.MutableState",
      // Flow
      "kotlinx.coroutines.flow.MutableSharedFlow",
      "kotlinx.coroutines.flow.MutableStateFlow",
      // RxJava
      "io.reactivex.rxjava3.subjects.BehaviorSubject",
      "io.reactivex.rxjava3.subjects.PublishSubject",
      "io.reactivex.rxjava3.subjects.ReplaySubject",
      "io.reactivex.subjects.BehaviorSubject",
      "io.reactivex.subjects.PublishSubject",
      "io.reactivex.subjects.ReplaySubject",
      "rx.subjects.BehaviorSubject",
      "rx.subjects.PublishSubject",
      "rx.subjects.ReplaySubject",
      // RxRelay
      "com.jakewharton.rxrelay.BehaviorRelay",
      "com.jakewharton.rxrelay.PublishRelay",
      "com.jakewharton.rxrelay.ReplayRelay",
      "com.jakewharton.rxrelay2.BehaviorRelay",
      "com.jakewharton.rxrelay2.PublishRelay",
      "com.jakewharton.rxrelay2.ReplayRelay",
      "com.jakewharton.rxrelay3.BehaviorRelay",
      "com.jakewharton.rxrelay3.PublishRelay",
      "com.jakewharton.rxrelay3.ReplayRelay",
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
