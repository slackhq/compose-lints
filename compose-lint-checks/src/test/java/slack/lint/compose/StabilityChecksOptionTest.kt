// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Option
import org.junit.Assert.assertEquals
import org.junit.Test

class StabilityChecksOptionTest {

  /**
   * Regression test for a CI-only flake: lint stores a single mutable `issue` back-reference on an
   * [Option] when it's registered, and uses it to scope config lookups. A single `stability-checks`
   * option instance shared across the stability issues had its `issue` overwritten by whichever
   * detector registered last (order-dependent), so the `configureOption(...)` value was read from
   * the wrong issue's scope and the check silently disabled itself. Each detector must own its
   * instance.
   */
  @Test
  fun `each stability option is registered to its own issue`() {
    // Capture both options up front so every detector has registered before we read any
    // `issue`. (If they shared one instance, the back-reference would point at whichever registered
    // last; reading inline would mask that since each access re-registers just-in-time.)
    val receiverOption = UnstableReceiverDetector.STABILITY_CHECKS_OPTION
    val collectionsOption = UnstableCollectionsDetector.STABILITY_CHECKS_OPTION

    assertEquals("ComposeUnstableReceiver", receiverOption.issue.id)
    assertEquals("ComposeUnstableCollections", collectionsOption.issue.id)
  }
}
