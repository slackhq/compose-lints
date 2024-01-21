// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.toUElementOfType

private const val COMPOSE_PREVIEW = "androidx.compose.ui.tooling.preview.Preview"
private const val COMPOSE_DESKTOP_PREVIEW =
  "androidx.compose.desktop.ui.tooling.preview.Preview"

val UAnnotated.isPreview: Boolean
  get() = findAnnotation(COMPOSE_PREVIEW) != null ||
    findAnnotation(COMPOSE_DESKTOP_PREVIEW) != null ||
    uAnnotations.any {
      it.resolve()?.toUElementOfType<UClass>()?.let { cls ->
        cls.findAnnotation(COMPOSE_PREVIEW) != null ||
          cls.findAnnotation(COMPOSE_DESKTOP_PREVIEW) != null
      } ?: false
    }

val KtAnnotationEntry.isPreviewAnnotation: Boolean
  get() = calleeExpression?.text?.let { PreviewNameRegex.matches(it) } == true

val UParameter.isPreviewParameter: Boolean
  get() = findAnnotation("androidx.compose.ui.tooling.preview.PreviewParameter") != null

val PreviewNameRegex by lazy { Regex(".*Preview[s]*$") }
