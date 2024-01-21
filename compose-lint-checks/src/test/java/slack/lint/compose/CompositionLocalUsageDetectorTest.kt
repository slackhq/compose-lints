// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class CompositionLocalUsageDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = CompositionLocalUsageDetector()

  override fun getIssues(): List<Issue> = listOf(CompositionLocalUsageDetector.ISSUE)

  override fun lint(): TestLintTask {
    return super.lint()
      .configureOption(CompositionLocalUsageDetector.ALLOW_LIST, "LocalBanana,LocalPotato")
  }

  @Test
  fun `warning when a CompositionLocal is defined`() {
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
      .expectWarningCount(4)
      .expect(
        testMode = TestMode.PARENTHESIZED,
        expectedText =
          """
          src/test.kt:2: Warning: `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.See https://slackhq.github.io/compose-lints/rules/#compositionlocals for more information. [ComposeCompositionLocalUsage]
                          private val LocalApple = (staticCompositionLocalOf<String> { "Apple" })
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:3: Warning: `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.See https://slackhq.github.io/compose-lints/rules/#compositionlocals for more information. [ComposeCompositionLocalUsage]
                          internal val LocalPlum: String = (staticCompositionLocalOf { "Plum" })
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:4: Warning: `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.See https://slackhq.github.io/compose-lints/rules/#compositionlocals for more information. [ComposeCompositionLocalUsage]
                          val LocalPrune = (compositionLocalOf { "Prune" })
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:5: Warning: `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.See https://slackhq.github.io/compose-lints/rules/#compositionlocals for more information. [ComposeCompositionLocalUsage]
                          private val LocalKiwi: String = (compositionLocalOf { "Kiwi" })
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 4 warnings
        """
            .trimIndent()
      )
      .expect(
        """
        src/test.kt:2: Warning: `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.See https://slackhq.github.io/compose-lints/rules/#compositionlocals for more information. [ComposeCompositionLocalUsage]
                        private val LocalApple = staticCompositionLocalOf<String> { "Apple" }
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test.kt:3: Warning: `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.See https://slackhq.github.io/compose-lints/rules/#compositionlocals for more information. [ComposeCompositionLocalUsage]
                        internal val LocalPlum: String = staticCompositionLocalOf { "Plum" }
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test.kt:4: Warning: `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.See https://slackhq.github.io/compose-lints/rules/#compositionlocals for more information. [ComposeCompositionLocalUsage]
                        val LocalPrune = compositionLocalOf { "Prune" }
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test.kt:5: Warning: `CompositionLocal`s are implicit dependencies and creating new ones should be avoided.See https://slackhq.github.io/compose-lints/rules/#compositionlocals for more information. [ComposeCompositionLocalUsage]
                        private val LocalKiwi: String = compositionLocalOf { "Kiwi" }
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 4 warnings
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
