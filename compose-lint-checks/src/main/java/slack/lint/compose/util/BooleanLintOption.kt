// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.detector.api.BooleanOption

open class BooleanLintOption(private val option: BooleanOption) : LintOption {
  var value: Boolean = option.defaultValue
    private set

  override fun load(configuration: Configuration) {
    value = option.getValue(configuration)
  }
}
