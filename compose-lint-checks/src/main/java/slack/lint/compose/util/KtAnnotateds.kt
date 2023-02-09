// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import org.jetbrains.kotlin.psi.KtAnnotated

val KtAnnotated.isComposable: Boolean
  get() = annotationEntries.any { it.calleeExpression?.text == "Composable" }
