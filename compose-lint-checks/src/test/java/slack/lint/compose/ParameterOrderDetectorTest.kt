// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class ParameterOrderDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = ParameterOrderDetector()

  override fun getIssues(): List<Issue> = listOf(ParameterOrderDetector.ISSUE)

  override val skipTestModes
    get() = arrayOf(TestMode.TYPE_ALIAS)

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
        fun MyComposable(text1: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) { }

        @Composable
        fun MyComposable(text1: String, modifier: Modifier = Modifier, m2: Modifier = Modifier, trailing: @Composable () -> Unit) { }

        @Composable
        fun MyComposable(text1: String, modifier: Modifier = Modifier, m2: Modifier = Modifier, trailing: (@Composable () -> Unit)?) { }

        @Composable
        inline fun <reified T> MyComposable(modifier: Modifier = Modifier, text: String = "123", lambda: () -> Unit) : T { }

        @Composable
        fun MyComposable(enabled: Boolean = false, content: @Composable BoxScope.() -> Unit) { }

        @Composable
        fun BoxScope.MyComposable(content: BoxScope.() -> Unit) { }
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
        import androidx.compose.foundation.layout

        @Composable
        fun MyComposable(modifier: Modifier = Modifier, other: String, other2: String) { }

        @Composable
        fun MyComposable(text: String = "deffo", modifier: Modifier = Modifier) { }

        @Composable
        fun MyComposable(modifier: Modifier = Modifier, text: String = "123", modifier2: Modifier = Modifier) { }

        @Composable
        fun MyComposable(text: String = "123", modifier: Modifier = Modifier, onClick: () -> Unit) { }

        @Composable
        fun MyComposable(text1: String, m2: Modifier = Modifier, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) { }

        @Composable
        inline fun <reified T> MyComposable(text: String = "123", modifier: Modifier = Modifier, lambda: () -> Unit) : T { }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, boxScope, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:6: Error: Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
          Current params are: [modifier: Modifier = Modifier, other: String, other2: String] but should be [other: String, other2: String, modifier: Modifier = Modifier].
          See https://slackhq.github.io/compose-lints/rules/#ordering-composable-parameters-properly for more information. [ComposeParameterOrder]
          fun MyComposable(modifier: Modifier = Modifier, other: String, other2: String) { }
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:9: Error: Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
          Current params are: [text: String = "deffo", modifier: Modifier = Modifier] but should be [modifier: Modifier = Modifier, text: String = "deffo"].
          See https://slackhq.github.io/compose-lints/rules/#ordering-composable-parameters-properly for more information. [ComposeParameterOrder]
          fun MyComposable(text: String = "deffo", modifier: Modifier = Modifier) { }
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:12: Error: Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
          Current params are: [modifier: Modifier = Modifier, text: String = "123", modifier2: Modifier = Modifier] but should be [modifier: Modifier = Modifier, modifier2: Modifier = Modifier, text: String = "123"].
          See https://slackhq.github.io/compose-lints/rules/#ordering-composable-parameters-properly for more information. [ComposeParameterOrder]
          fun MyComposable(modifier: Modifier = Modifier, text: String = "123", modifier2: Modifier = Modifier) { }
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:15: Error: Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
          Current params are: [text: String = "123", modifier: Modifier = Modifier, onClick: () -> Unit] but should be [onClick: () -> Unit, modifier: Modifier = Modifier, text: String = "123"].
          See https://slackhq.github.io/compose-lints/rules/#ordering-composable-parameters-properly for more information. [ComposeParameterOrder]
          fun MyComposable(text: String = "123", modifier: Modifier = Modifier, onClick: () -> Unit) { }
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:18: Error: Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
          Current params are: [text1: String, m2: Modifier = Modifier, modifier: Modifier = Modifier, trailing: @Composable () -> Unit] but should be [text1: String, modifier: Modifier = Modifier, m2: Modifier = Modifier, trailing: @Composable () -> Unit].
          See https://slackhq.github.io/compose-lints/rules/#ordering-composable-parameters-properly for more information. [ComposeParameterOrder]
          fun MyComposable(text1: String, m2: Modifier = Modifier, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) { }
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:21: Error: Parameters in a composable function should be ordered following this pattern: params without defaults, modifiers, params with defaults and optionally, a trailing function that might not have a default param.
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
          Fix for src/test.kt line 6: Reorder parameters:
          @@ -6 +6
          - fun MyComposable(modifier: Modifier = Modifier, other: String, other2: String) { }
          + fun MyComposable(other: String, other2: String, modifier: Modifier = Modifier) { }
          Fix for src/test.kt line 9: Reorder parameters:
          @@ -9 +9
          - fun MyComposable(text: String = "deffo", modifier: Modifier = Modifier) { }
          + fun MyComposable(modifier: Modifier = Modifier, text: String = "deffo") { }
          Fix for src/test.kt line 12: Reorder parameters:
          @@ -12 +12
          - fun MyComposable(modifier: Modifier = Modifier, text: String = "123", modifier2: Modifier = Modifier) { }
          + fun MyComposable(modifier: Modifier = Modifier, modifier2: Modifier = Modifier, text: String = "123") { }
          Fix for src/test.kt line 15: Reorder parameters:
          @@ -15 +15
          - fun MyComposable(text: String = "123", modifier: Modifier = Modifier, onClick: () -> Unit) { }
          + fun MyComposable(onClick: () -> Unit, modifier: Modifier = Modifier, text: String = "123") { }
          Fix for src/test.kt line 18: Reorder parameters:
          @@ -18 +18
          - fun MyComposable(text1: String, m2: Modifier = Modifier, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) { }
          + fun MyComposable(text1: String, modifier: Modifier = Modifier, m2: Modifier = Modifier, trailing: @Composable () -> Unit) { }
          Fix for src/test.kt line 21: Reorder parameters:
          @@ -21 +21
          - inline fun <reified T> MyComposable(text: String = "123", modifier: Modifier = Modifier, lambda: () -> Unit) : T { }
          + inline fun <reified T> MyComposable(modifier: Modifier = Modifier, text: String = "123", lambda: () -> Unit) : T { }
        """
          .trimIndent()
      )
  }

  @Test
  fun `lint fix preserves code structure`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun MyComposable(modifier: Modifier = Modifier, other: String) { }

        @Composable
        fun MyComposable(
            modifier: Modifier = Modifier,
            other: String,
        ) { }

        @Composable
        fun MyComposable(
            modifier: Modifier = Modifier, // comments stay where they are
            other: String
        ) { }

        @Composable
        fun Modifier.clickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = this
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
          Fix for src/test.kt line 5: Reorder parameters:
          @@ -5 +5
            @Composable
          - fun MyComposable(modifier: Modifier = Modifier, other: String) { }
          + fun MyComposable(other: String, modifier: Modifier = Modifier) { }
          Fix for src/test.kt line 8: Reorder parameters:
          @@ -9 +9
            fun MyComposable(
          -     modifier: Modifier = Modifier,
                other: String,
          +     modifier: Modifier = Modifier,
            ) { }
          Fix for src/test.kt line 14: Reorder parameters:
          @@ -15 +15
            fun MyComposable(
          -     modifier: Modifier = Modifier, // comments stay where they are
          -     other: String
          +     other: String, // comments stay where they are
          +     modifier: Modifier = Modifier
            ) { }
          Fix for src/test.kt line 20: Reorder parameters:
          @@ -20 +20
            @Composable
          - fun Modifier.clickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = this
          + fun Modifier.clickable(onClick: () -> Unit, enabled: Boolean = true): Modifier = this
        """
          .trimIndent()
      )
  }

  private val boxScope =
    kotlin(
      """
      package androidx.compose.foundation.layout
      object BoxScope
    """
        .trimIndent()
    )
}
