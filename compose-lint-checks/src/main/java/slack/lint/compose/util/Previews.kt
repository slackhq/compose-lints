// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry

val KtAnnotated.isPreview: Boolean
  get() = annotationEntries.any { it.isPreviewAnnotation }

val KtAnnotationEntry.isPreviewAnnotation: Boolean
  get() = calleeExpression?.text?.let { PreviewNameRegex.matches(it) } == true

val KtAnnotated.isPreviewParameter: Boolean
  get() = annotationEntries.any { it.calleeExpression?.text == "PreviewParameter" }

val PreviewNameRegex by lazy { Regex(".*Preview[s]*$") }
