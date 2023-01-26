// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class ComposeCompositionLocalUsageDetectorTest : BaseSlackLintTest() {

  override fun getDetector(): Detector = ComposeCompositionLocalUsageDetector()
  override fun getIssues(): List<Issue> = listOf(ComposeCompositionLocalUsageDetector.ISSUE)

  override fun lint(): TestLintTask {
    return super.lint()
      .configureOption(ComposeCompositionLocalUsageDetector.ALLOW_LIST, "LocalBanana,LocalPotato")
  }

  // This mode is irrelevant to our test and totally untestable with stringy outputs
  override val skipTestModes: Array<TestMode> = arrayOf(TestMode.PARENTHESIZED)

  @Test
  fun `error when a CompositionLocal is defined`() {
    lint()
      .files(
        kotlin(
          """
                private val LocalApple = staticCompositionLocalOf<String> { "Apple" }
                internal val LocalPlum: String = staticCompositionLocalOf { "Plum" }
                val LocalPrune = compositionLocalOf { "Prune" }
                private val LocalKiwi: String = compositionLocalOf { "Kiwi" }
            """
        )
      )
      .allowCompilationErrors()
      .run()
      .expectErrorCount(4)
      .expect(
        """
        src/test.kt:2: Error: `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.
        See https://twitter.github.io/compose-rules/rules/#compositionlocals for more information. [ComposeCompositionLocalUsage]
                        private val LocalApple = staticCompositionLocalOf<String> { "Apple" }
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test.kt:3: Error: `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.
        See https://twitter.github.io/compose-rules/rules/#compositionlocals for more information. [ComposeCompositionLocalUsage]
                        internal val LocalPlum: String = staticCompositionLocalOf { "Plum" }
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test.kt:4: Error: `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.
        See https://twitter.github.io/compose-rules/rules/#compositionlocals for more information. [ComposeCompositionLocalUsage]
                        val LocalPrune = compositionLocalOf { "Prune" }
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test.kt:5: Error: `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.
        See https://twitter.github.io/compose-rules/rules/#compositionlocals for more information. [ComposeCompositionLocalUsage]
                        private val LocalKiwi: String = compositionLocalOf { "Kiwi" }
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        4 errors, 0 warnings
      """
          .trimIndent()
      )
  }

  @Test
  fun `passes when a CompositionLocal is defined but it's in the allowlist`() {
    lint()
      .files(
        kotlin(
          """
                val LocalBanana = staticCompositionLocalOf<String> { "Banana" }
                val LocalPotato = compositionLocalOf { "Potato" }
            """
        )
      )
      .allowCompilationErrors()
      .run()
      .expectClean()
  }
}
