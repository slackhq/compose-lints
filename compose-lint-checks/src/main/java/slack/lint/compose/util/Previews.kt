// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UParameter

private const val COMPOSE_PREVIEW = "androidx.compose.ui.tooling.preview.Preview"
private const val COMPOSE_DESKTOP_PREVIEW = "androidx.compose.desktop.ui.tooling.preview.Preview"

val PREVIEW_ANNOTATIONS = setOf(COMPOSE_PREVIEW, COMPOSE_DESKTOP_PREVIEW)
val TEST_ANNOTATIONS =
  setOf(
    "org.jetbrains.annotations.TestOnly",
    "com.google.common.annotations.VisibleForTesting",
    "androidx.annotation.VisibleForTesting",
  )

val UAnnotated.isPreview: Boolean
  get() =
    uAnnotations.any {
      it.resolve()?.let { cls ->
        cls.qualifiedName in PREVIEW_ANNOTATIONS ||
          // Is the annotation itself a preview-annotated annotation?
          cls.hasAnnotation(COMPOSE_PREVIEW) ||
          cls.hasAnnotation(COMPOSE_DESKTOP_PREVIEW)
      } ?: false
    }

val UAnnotated.isVisibleForTesting: Boolean
  get() =
    uAnnotations.any {
      // Is it itself a preview annotation?
      it.resolve()?.let { cls -> cls.qualifiedName in TEST_ANNOTATIONS } ?: false
    }

val UParameter.isPreviewParameter: Boolean
  get() = findAnnotation("androidx.compose.ui.tooling.preview.PreviewParameter") != null
