// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.google.auto.service.AutoService

@AutoService(IssueRegistry::class)
class ComposeLintsIssueRegistry : IssueRegistry() {

  override val vendor: Vendor =
    Vendor(
      vendorName = "slack",
      identifier = "com.slack.lint.compose:compose-lints",
      feedbackUrl = "https://github.com/slackhq/compose-lints/issues",
    )

  override val api: Int = CURRENT_API
  override val minApi: Int = 13 // 7.3.0-alpha02

  @Suppress("SpreadOperator")
  override val issues: List<Issue> =
    listOf(
      *ComposeNamingDetector.ISSUES,
      ComposeCompositionLocalUsageDetector.ISSUE,
      ComposeContentEmitterReturningValuesDetector.ISSUE,
      ComposeModifierMissingDetector.ISSUE,
      ComposeModifierReusedDetector.ISSUE,
      ComposeModifierWithoutDefaultDetector.ISSUE,
      ComposeMultipleContentEmittersDetector.ISSUE,
      ComposeMutableParametersDetector.ISSUE,
      ComposeParameterOrderDetector.ISSUE,
      ComposePreviewNamingDetector.ISSUE,
      ComposePreviewPublicDetector.ISSUE,
      ComposeRememberMissingDetector.ISSUE,
      ComposeUnstableCollectionsDetector.ISSUE,
    )
}
