// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.detector.api.Issue

/**
 * A layer of indirection for implementations of option loaders without needing to extend from
 * Detector. This goes along with [OptionLoadingDetector].
 */
interface LintOption {
  fun load(configuration: Configuration, issue: Issue)
}
