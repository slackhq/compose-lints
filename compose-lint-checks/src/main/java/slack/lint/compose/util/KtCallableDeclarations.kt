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
    "androidx.collection.MutableScatterSet",
    "android.util.ArraySet",
    "androidx.collection.ArraySet",
    "java.util.HashSet",
    "java.util.LinkedHashSet",
    "java.util.TreeSet",
    "java.util.concurrent.CopyOnWriteArraySet",
    "java.util.concurrent.ConcurrentSkipListSet",
    // List
    "androidx.collection.MutableObjectList",
    "java.util.ArrayList",
    "java.util.LinkedList",
    "java.util.Vector",
    "java.util.Stack",
    "java.util.concurrent.CopyOnWriteArrayList",
    // Array
    "android.util.SparseArray",
    "androidx.collection.SparseArrayCompat",
    "android.util.LongSparseArray",
    "androidx.collection.LongSparseArray",
    "android.util.SparseBooleanArray",
    "android.util.SparseIntArray",
    // Map
    "androidx.collection.MutableScatterMap",
    "androidx.collection.MutableIntObjectMap",
    "androidx.collection.MutableObjectIntMap",
    "androidx.collection.MutableLongObjectMap",
    "androidx.collection.MutableObjectLongMap",
    "java.util.HashMap",
    "java.util.LinkedHashMap",
    "java.util.TreeMap",
    "java.util.Hashtable",
    "java.util.concurrent.ConcurrentHashMap",
    "java.util.concurrent.ConcurrentSkipListMap",
    "java.util.IdentityHashMap",
    "java.util.WeakHashMap",
    // Queue
    "java.util.ArrayDeque",
    "kotlin.collections.ArrayDeque",
    "java.util.PriorityQueue",
  )

val KnownMutableCommonTypes =
  KnownMutableCommonCollectionTypes +
    setOf(
      // Compose
      "androidx.compose.runtime.MutableState",
      // Flow
      "kotlinx.coroutines.flow.MutableStateFlow",
      "kotlinx.coroutines.flow.MutableSharedFlow",
      // RxJava
      "rx.subjects.PublishSubject",
      "rx.subjects.BehaviorSubject",
      "rx.subjects.ReplaySubject",
      "io.reactivex.subjects.PublishSubject",
      "io.reactivex.subjects.BehaviorSubject",
      "io.reactivex.subjects.ReplaySubject",
      "io.reactivex.rxjava3.subjects.PublishSubject",
      "io.reactivex.rxjava3.subjects.BehaviorSubject",
      "io.reactivex.rxjava3.subjects.ReplaySubject",
      // RxRelay
      "com.jakewharton.rxrelay.PublishRelay",
      "com.jakewharton.rxrelay.BehaviorRelay",
      "com.jakewharton.rxrelay.ReplayRelay",
      "com.jakewharton.rxrelay2.PublishRelay",
      "com.jakewharton.rxrelay2.BehaviorRelay",
      "com.jakewharton.rxrelay2.ReplayRelay",
      "com.jakewharton.rxrelay3.PublishRelay",
      "com.jakewharton.rxrelay3.BehaviorRelay",
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
