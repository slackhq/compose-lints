// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import java.util.Locale

fun <T> T.runIf(value: Boolean, block: T.() -> T): T = if (value) block() else this

fun String?.matchesAnyOf(patterns: Sequence<Regex>): Boolean {
  if (isNullOrEmpty()) return false
  for (regex in patterns) {
    if (matches(regex)) return true
  }
  return false
}

fun String.toCamelCase() =
  split('_')
    .joinToString(
      separator = "",
      transform = { original ->
        original.replaceFirstChar {
          if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
      }
    )

fun String.toSnakeCase() = replace(humps, "_").lowercase(Locale.getDefault())

private val humps by lazy(LazyThreadSafetyMode.NONE) { "(?<=.)(?=\\p{Upper})".toRegex() }
