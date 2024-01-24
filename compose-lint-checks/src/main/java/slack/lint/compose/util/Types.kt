// Copyright (C) 2024 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.intellij.psi.PsiClass

val PsiClass.isFunctionalInterface: Boolean
  get() {
    return hasAnnotation("java.lang.FunctionalInterface") ||
      qualifiedName == "kotlin.Function" ||
      qualifiedName?.startsWith("kotlin.jvm.functions.") == true
  }

val PsiClass.allSupertypes: Sequence<PsiClass>
  get() {
    return sequenceOf(this) +
      superTypes
        .asSequence()
        .distinct()
        .mapNotNull { it.resolve() }
        .filterNot { it.qualifiedName == "java.lang.Object" }
        .flatMap { resolved -> sequenceOf(resolved).plus(resolved.allSupertypes) }
        .distinct()
  }
