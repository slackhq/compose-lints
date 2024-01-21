// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class ComposeLintsIssueRegistryTest : BaseComposeLintTest() {
  @Test
  fun ensureUniqueIds() {
    val issues = ComposeLintsIssueRegistry().issues
    if (issues.distinctBy { it.id }.size != issues.size) {
      fail(
        "Duplicate issue IDs found!\n${issues.groupBy { it.id }.filter { it.value.size > 1 }.entries.joinToString("\n") { (key, value) -> "${key}=${value.map { it.implementation.detectorClass.simpleName }}" } }"
      )
    }
  }

  override fun getDetector(): Detector {
    throw NotImplementedError()
  }

  override fun getIssues(): List<Issue> {
    throw NotImplementedError()
  }
}
