// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class RememberMissingDetectorTest : BaseSlackLintTest() {

  override fun getDetector(): Detector = RememberMissingDetector()

  override fun getIssues(): List<Issue> = listOf(RememberMissingDetector.ISSUE)

  @Test
  fun `passes when a non-remembered mutableStateOf is used outside of a Composable`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.mutableStateOf
        val msof = mutableStateOf("X")
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `errors when a non-remembered mutableStateOf is used in a Composable`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.mutableStateOf
        import androidx.compose.runtime.State

        @Composable
        fun MyComposable() {
            val something = mutableStateOf("X")
        }
        @Composable
        fun MyComposable(something: State<String> = mutableStateOf("X")) {
        }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:7: Error: Using mutableStateOf in a @Composable function without it being inside of a remember function.
          If you don't remember the state instance, a new state instance will be created when the function is recomposed.
          See https://slackhq.github.io/compose-lints/rules/#state-should-be-remembered-in-composables for more information. [ComposeRememberMissing]
              val something = mutableStateOf("X")
                              ~~~~~~~~~~~~~~~~~~~
          src/test.kt:10: Error: Using mutableStateOf in a @Composable function without it being inside of a remember function.
          If you don't remember the state instance, a new state instance will be created when the function is recomposed.
          See https://slackhq.github.io/compose-lints/rules/#state-should-be-remembered-in-composables for more information. [ComposeRememberMissing]
          fun MyComposable(something: State<String> = mutableStateOf("X")) {
                                                      ~~~~~~~~~~~~~~~~~~~
          2 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `passes when a remembered mutableStateOf is used in a Composable`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.mutableStateOf
        import androidx.compose.runtime.State

        @Composable
        fun MyComposable(
            something: State<String> = remember { mutableStateOf("X") }
        ) {
            val something = remember { mutableStateOf("X") }
            val something2 by remember { mutableStateOf("Y") }
        }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `passes when a rememberSaveable mutableStateOf is used in a Composable`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.mutableStateOf
        import androidx.compose.runtime.State

        @Composable
        fun MyComposable(
            something: State<String> = rememberSaveable { mutableStateOf("X") }
        ) {
            val something = rememberSaveable { mutableStateOf("X") }
            val something2 by rememberSaveable { mutableStateOf("Y") }
        }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `passes when a non-remembered derivedStateOf is used outside of a Composable`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.derivedStateOf

        val dsof = derivedStateOf { "X" }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `errors when a non-remembered derivedStateOf is used in a Composable`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.State
        import androidx.compose.runtime.derivedStateOf

        @Composable
        fun MyComposable() {
            val something = derivedStateOf { "X" }
        }
        @Composable
        fun MyComposable(something: State<String> = derivedStateOf { "X" }) {
        }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:7: Error: Using derivedStateOf in a @Composable function without it being inside of a remember function.
          If you don't remember the state instance, a new state instance will be created when the function is recomposed.
          See https://slackhq.github.io/compose-lints/rules/#state-should-be-remembered-in-composables for more information. [ComposeRememberMissing]
              val something = derivedStateOf { "X" }
                              ~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:10: Error: Using derivedStateOf in a @Composable function without it being inside of a remember function.
          If you don't remember the state instance, a new state instance will be created when the function is recomposed.
          See https://slackhq.github.io/compose-lints/rules/#state-should-be-remembered-in-composables for more information. [ComposeRememberMissing]
          fun MyComposable(something: State<String> = derivedStateOf { "X" }) {
                                                      ~~~~~~~~~~~~~~~~~~~~~~
          2 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `passes when a remembered derivedStateOf is used in a Composable`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.State
        import androidx.compose.runtime.derivedStateOf
        import androidx.compose.runtime.remember

        @Composable
        fun MyComposable(
            something: State<String> = remember { derivedStateOf { "X" } }
        ) {
            val something = remember { derivedStateOf { "X" } }
            val something2 by remember { derivedStateOf { "Y" } }
        }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }
}
