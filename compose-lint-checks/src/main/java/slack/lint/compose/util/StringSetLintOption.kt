// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.StringOption

open class StringSetLintOption(private val option: StringOption) : LintOption {
  var value: Set<String> = emptySet()
    private set

  override fun load(configuration: Configuration, issue: Issue) {
    value = option.loadAsSet(configuration, issue)
  }
}
