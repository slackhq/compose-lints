// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

/**
 * Builds deterministic lint issue/report text from readable multiline source strings.
 *
 * Blank lines delimit output paragraphs. Lines inside each paragraph are trimmed and joined with
 * single spaces, avoiding fragile trailing-space + backslash continuations in source.
 */
internal fun issueText(markdown: String): String {
  val paragraphs = markdown.trimIndent().split(Regex("\n\\s*\n"))
  return paragraphs.joinToString(separator = "\n\n") { paragraph ->
    paragraph.lineSequence().map(String::trim).filter(String::isNotEmpty).joinToString(" ")
  }
}
