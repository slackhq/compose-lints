// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.detector.api.BooleanOption
import com.android.tools.lint.detector.api.JavaContext

/**
 * Shared option gating the Compose stability checks (`ComposeUnstableReceiver`,
 * `ComposeMutableParameters`, and `ComposeUnstableCollections`).
 *
 * These are significantly less important in the era of Compose strong skipping, which makes
 * composables skippable regardless of parameter/receiver stability, so they are disabled by default
 * and must be explicitly enabled.
 */
val STABILITY_CHECKS_OPTION =
  BooleanOption(
    "stability-checks",
    "Enable Compose stability checks (unstable receivers, mutable parameters, unstable collections)",
    false,
    "Stability-related checks are significantly less important now that Compose strong skipping is " +
      "widely available, so they are disabled by default. Set this to true to re-enable them. See " +
      "https://slackhq.github.io/compose-lints/rules/#stability for more information.",
  )

/** Whether the [STABILITY_CHECKS_OPTION] is enabled for the current [JavaContext]. */
fun JavaContext.stabilityChecksEnabled(): Boolean = STABILITY_CHECKS_OPTION.getValue(this)
