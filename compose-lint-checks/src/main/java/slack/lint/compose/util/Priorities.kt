// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

/**
 * Priorities with semantic names. Partially for readability, partially so detekt stops nagging
 * about MagicNumber.
 */
object Priorities {
  const val HIGH = 10
  const val NORMAL = 5
  const val LOW = 3
  const val NONE = 1
}
