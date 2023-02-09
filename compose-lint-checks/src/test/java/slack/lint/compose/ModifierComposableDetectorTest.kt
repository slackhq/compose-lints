// Copyright (C) 2023 Slack Technologies, LLC
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class ModifierComposableDetectorTest : BaseSlackLintTest() {

  override fun getDetector(): Detector = ModifierComposableDetector()
  override fun getIssues(): List<Issue> = listOf(ModifierComposableDetector.ISSUE)

  // This mode is irrelevant to our test and totally untestable with stringy outputs
  override val skipTestModes: Array<TestMode> = arrayOf(TestMode.SUPPRESSIBLE, TestMode.TYPE_ALIAS)

  @Test
  fun `errors when a composable Modifier extension is detected`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun Modifier.something1(): Modifier { }
        @Composable
        fun Modifier.something2() = somethingElse()
      """
        .trimIndent()

    lint()
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/test.kt:1: Error: Using @Composable builder functions for modifiers is not recommended, as they cause unnecessary recompositions.
          You should use Modifier.composed { ... } instead, as it limits recomposition to just the modifier instance, rather than the whole function tree.
          See https://slackhq.github.io/compose-lints/rules/#avoid-modifier-extension-factory-functions for more information. [ComposeComposableModifier]
          @Composable
          ^
          src/test.kt:3: Error: Using @Composable builder functions for modifiers is not recommended, as they cause unnecessary recompositions.
          You should use Modifier.composed { ... } instead, as it limits recomposition to just the modifier instance, rather than the whole function tree.
          See https://slackhq.github.io/compose-lints/rules/#avoid-modifier-extension-factory-functions for more information. [ComposeComposableModifier]
          @Composable
          ^
          2 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `do not error on a regular composable`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun TextHolder(text: String) {}
      """
        .trimIndent()

    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }
}
