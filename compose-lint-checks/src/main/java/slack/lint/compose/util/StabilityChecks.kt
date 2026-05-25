// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.detector.api.BooleanOption

/**
 * Builds the `stability-checks` option that gates the Compose stability checks
 * (`ComposeUnstableReceiver`, `ComposeMutableParameters`, and `ComposeUnstableCollections`).
 *
 * Each detector must create and register its **own** instance. Lint stores a single back-reference
 * ([com.android.tools.lint.detector.api.Option.issue]) on an option when it's registered with an
 * `Issue`, and `Configuration.getOption` uses that back-reference to scope configuration lookups. A
 * single instance shared across issues would therefore have its `issue` overwritten by whichever
 * detector registers last (order-dependent across class-loading), causing configured values to be
 * read from the wrong issue's scope. All instances share the same name, so from a user's
 * perspective it's still a single `stability-checks` option (set per issue).
 *
 * These checks are significantly less important in the era of Compose strong skipping, which makes
 * composables skippable regardless of parameter/receiver stability, so they are disabled by default
 * and must be explicitly enabled.
 */
fun stabilityChecksOption(): BooleanOption =
  BooleanOption(
    "stability-checks",
    "Enable Compose stability checks (unstable receivers, mutable parameters, unstable collections)",
    false,
    "Stability-related checks are significantly less important now that Compose strong skipping is " +
      "widely available, so they are disabled by default. Set this to true to re-enable them. See " +
      "https://slackhq.github.io/compose-lints/rules/#stability for more information.",
  )
