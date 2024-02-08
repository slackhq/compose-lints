// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class ParameterOrderDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = ParameterOrderDetector()

  override fun getIssues(): List<Issue> = listOf(ParameterOrderDetector.ISSUE)

  @Test
  fun `no errors when ordering is correct`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        fun MyComposable(text1: String, modifier: Modifier = Modifier, other: String = "1", other2: String = "2") { }

        @Composable
        fun MyComposable(text1: String, modifier: Modifier = Modifier, other2: String = "2", other : String = "1") { }

        @Composable
        fun MyComposable(text1: String, modifier: Modifier = Modifier, trailing: () -> Unit) { }

        @Composable
        fun MyComposable(text1: String, modifier: Modifier = Modifier, m2: Modifier = Modifier, trailing: () -> Unit) { }

        @Composable
        fun MyComposable(text1: String, modifier: Modifier = Modifier, m2: Modifier = Modifier, trailing: (() -> Unit)?) { }

        @Composable
        inline fun <reified T> MyComposable(modifier: Modifier = Modifier, text: String = "123", lambda: () -> Unit) : T { }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `errors found when ordering is wrong`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun MyComposable(modifier: Modifier = Modifier, other: String, other2: String) { }

        @Composable
        fun MyComposable(text: String = "deffo", modifier: Modifier = Modifier) { }

        @Composable
        fun MyComposable(modifier: Modifier = Modifier, text: String = "123", modifier2: Modifier = Modifier) { }

        @Composable
        fun MyComposable(text: String = "123", modifier: Modifier = Modifier, lambda: () -> Unit) { }

        @Composable
        fun MyComposable(text1: String, m2: Modifier = Modifier, modifier: Modifier = Modifier, trailing: () -> Unit) { }

        @Composable
        inline fun <reified T> MyComposable(text: String = "123", modifier: Modifier = Modifier, lambda: () -> Unit) : T { }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:5: Error: Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
          Current params are: [modifier: Modifier = Modifier, other: String, other2: String] but should be [other: String, other2: String, modifier: Modifier = Modifier].
          See https://slackhq.github.io/compose-lints/rules/#ordering-composable-parameters-properly for more information. [ComposeParameterOrder]
          fun MyComposable(modifier: Modifier = Modifier, other: String, other2: String) { }
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:8: Error: Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
          Current params are: [text: String = "deffo", modifier: Modifier = Modifier] but should be [modifier: Modifier = Modifier, text: String = "deffo"].
          See https://slackhq.github.io/compose-lints/rules/#ordering-composable-parameters-properly for more information. [ComposeParameterOrder]
          fun MyComposable(text: String = "deffo", modifier: Modifier = Modifier) { }
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:11: Error: Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
          Current params are: [modifier: Modifier = Modifier, text: String = "123", modifier2: Modifier = Modifier] but should be [modifier: Modifier = Modifier, modifier2: Modifier = Modifier, text: String = "123"].
          See https://slackhq.github.io/compose-lints/rules/#ordering-composable-parameters-properly for more information. [ComposeParameterOrder]
          fun MyComposable(modifier: Modifier = Modifier, text: String = "123", modifier2: Modifier = Modifier) { }
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:14: Error: Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
          Current params are: [text: String = "123", modifier: Modifier = Modifier, lambda: () -> Unit] but should be [modifier: Modifier = Modifier, text: String = "123", lambda: () -> Unit].
          See https://slackhq.github.io/compose-lints/rules/#ordering-composable-parameters-properly for more information. [ComposeParameterOrder]
          fun MyComposable(text: String = "123", modifier: Modifier = Modifier, lambda: () -> Unit) { }
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:17: Error: Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
          Current params are: [text1: String, m2: Modifier = Modifier, modifier: Modifier = Modifier, trailing: () -> Unit] but should be [text1: String, modifier: Modifier = Modifier, m2: Modifier = Modifier, trailing: () -> Unit].
          See https://slackhq.github.io/compose-lints/rules/#ordering-composable-parameters-properly for more information. [ComposeParameterOrder]
          fun MyComposable(text1: String, m2: Modifier = Modifier, modifier: Modifier = Modifier, trailing: () -> Unit) { }
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:20: Error: Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
          Current params are: [text: String = "123", modifier: Modifier = Modifier, lambda: () -> Unit] but should be [modifier: Modifier = Modifier, text: String = "123", lambda: () -> Unit].
          See https://slackhq.github.io/compose-lints/rules/#ordering-composable-parameters-properly for more information. [ComposeParameterOrder]
          inline fun <reified T> MyComposable(text: String = "123", modifier: Modifier = Modifier, lambda: () -> Unit) : T { }
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          6 errors, 0 warnings
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
          Fix for src/test.kt line 5: Replace with (other: String, other2: String, modifier: Modifier = Modifier):
          @@ -5 +5
          - fun MyComposable(modifier: Modifier = Modifier, other: String, other2: String) { }
          + fun MyComposable(other: String, other2: String, modifier: Modifier = Modifier) { }
          Fix for src/test.kt line 8: Replace with (modifier: Modifier = Modifier, text: String = "deffo"):
          @@ -8 +8
          - fun MyComposable(text: String = "deffo", modifier: Modifier = Modifier) { }
          + fun MyComposable(modifier: Modifier = Modifier, text: String = "deffo") { }
          Fix for src/test.kt line 11: Replace with (modifier: Modifier = Modifier, modifier2: Modifier = Modifier, text: String = "123"):
          @@ -11 +11
          - fun MyComposable(modifier: Modifier = Modifier, text: String = "123", modifier2: Modifier = Modifier) { }
          + fun MyComposable(modifier: Modifier = Modifier, modifier2: Modifier = Modifier, text: String = "123") { }
          Fix for src/test.kt line 14: Replace with (modifier: Modifier = Modifier, text: String = "123", lambda: () -> Unit):
          @@ -14 +14
          - fun MyComposable(text: String = "123", modifier: Modifier = Modifier, lambda: () -> Unit) { }
          + fun MyComposable(modifier: Modifier = Modifier, text: String = "123", lambda: () -> Unit) { }
          Fix for src/test.kt line 17: Replace with (text1: String, modifier: Modifier = Modifier, m2: Modifier = Modifier, trailing: () -> Unit):
          @@ -17 +17
          - fun MyComposable(text1: String, m2: Modifier = Modifier, modifier: Modifier = Modifier, trailing: () -> Unit) { }
          + fun MyComposable(text1: String, modifier: Modifier = Modifier, m2: Modifier = Modifier, trailing: () -> Unit) { }
          Fix for src/test.kt line 20: Replace with (modifier: Modifier = Modifier, text: String = "123", lambda: () -> Unit):
          @@ -20 +20
          - inline fun <reified T> MyComposable(text: String = "123", modifier: Modifier = Modifier, lambda: () -> Unit) : T { }
          + inline fun <reified T> MyComposable(modifier: Modifier = Modifier, text: String = "123", lambda: () -> Unit) : T { }
        """
          .trimIndent()
      )
  }
}
