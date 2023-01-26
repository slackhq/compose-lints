// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class ComposeCompositionLocalNamingDetectorTest : BaseSlackLintTest() {

  override fun getDetector(): Detector = ComposeCompositionLocalNamingDetector()
  override fun getIssues(): List<Issue> = listOf(ComposeCompositionLocalNamingDetector.ISSUE)

  override fun lint(): TestLintTask {
    return super.lint()
      .configureOption(ComposeCompositionLocalNamingDetector.ALLOW_LIST, "Banana,Potato")
  }

  // This mode is irrelevant to our test and totally untestable with stringy outputs
  override val skipTestModes: Array<TestMode> = arrayOf(TestMode.PARENTHESIZED)

  @Test
  fun `error when a CompositionLocal has a wrong name`() {
    lint()
      .files(
        kotlin(
          """
                val AppleLocal = staticCompositionLocalOf<String> { "Apple" }
                val Plum: String = staticCompositionLocalOf { "Plum" }
            """
        )
      )
      .allowCompilationErrors()
      .run()
      .expect(
        """
        src/test.kt:2: Error: CompositionLocals should be named using the Local prefix as an adjective, followed by a descriptive noun.
        See https://twitter.github.io/compose-rules/rules/#naming-compositionlocals-properly for more information. [ComposeCompositionLocalNaming]
                        val AppleLocal = staticCompositionLocalOf<String> { "Apple" }
                            ~~~~~~~~~~
        src/test.kt:3: Error: CompositionLocals should be named using the Local prefix as an adjective, followed by a descriptive noun.
        See https://twitter.github.io/compose-rules/rules/#naming-compositionlocals-properly for more information. [ComposeCompositionLocalNaming]
                        val Plum: String = staticCompositionLocalOf { "Plum" }
                            ~~~~
        2 errors, 0 warnings
      """
          .trimIndent()
      )
  }

  @Test
  fun `passes when a CompositionLocal is well named or allow listed`() {
    lint()
      .files(
        kotlin(
          """
                val LocalBanana = staticCompositionLocalOf<String> { "Banana" }
                val LocalPotato = compositionLocalOf { "Potato" }
                val Banana = staticCompositionLocalOf<String> { "Banana" }
                val Potato = compositionLocalOf { "Potato" }
            """
        )
      )
      .allowCompilationErrors()
      .run()
      .expectClean()
  }
}
