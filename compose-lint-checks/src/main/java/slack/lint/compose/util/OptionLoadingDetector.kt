// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

/** A [Detector] that supports reading the given [options]. */
abstract class OptionLoadingDetector(private val options: List<Pair<LintOption, Issue>>) :
  Detector() {

  constructor(vararg options: Pair<LintOption, Issue>) : this(options.toList())

  override fun beforeCheckRootProject(context: Context) {
    super.beforeCheckRootProject(context)
    val config = context.findConfiguration(context.file)
    options.forEach { (option, issue) -> option.load(config, issue) }
  }
}
