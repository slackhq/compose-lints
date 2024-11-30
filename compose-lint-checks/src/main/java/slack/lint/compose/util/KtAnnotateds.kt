// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.intellij.psi.PsiType
import org.jetbrains.uast.UAnnotated

val UAnnotated.isComposable: Boolean
  get() = findAnnotation("androidx.compose.runtime.Composable") != null

val PsiType.isComposable: Boolean
  get() = hasAnnotation("androidx.compose.runtime.Composable")
