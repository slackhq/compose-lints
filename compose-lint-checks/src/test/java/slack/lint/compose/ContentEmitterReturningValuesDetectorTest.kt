// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class ContentEmitterReturningValuesDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = ContentEmitterReturningValuesDetector()

  override fun getIssues(): List<Issue> = listOf(ContentEmitterReturningValuesDetector.ISSUE)

  override fun lint(): TestLintTask {
    return super.lint()
      .configureOption(
        ContentEmitterReturningValuesDetector.CONTENT_EMITTER_OPTION,
        "Potato,Banana",
      )
  }

  @Test
  fun `passes when only one item emits up at the top level`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun Something() {
            val something = rememberWhatever()
            Column {
                Text("Hi")
                Text("Hola")
            }
            LaunchedEffect(Unit) {
            }
        }
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `passes when the composable is an extension function`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun ColumnScope.Something() {
            Text("Hi")
            Text("Hola")
        }
        @Composable
        fun RowScope.Something() {
            Spacer16()
            Text("Hola")
        }
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `errors when a Composable function has more than one UI emitter at the top level`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable

        @Composable
        fun Something() {
            Text("Hi")
            Text("Hola")
        }
        @Composable
        fun Something() {
            Spacer16()
            Text("Hola")
        }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:3: Error: Composable functions should either emit content into the composition or return a value, but not both.If a composable should offer additional control surfaces to its caller, those control surfaces or callbacks should be provided as parameters to the composable function by the caller.See https://slackhq.github.io/compose-lints/rules/#do-not-emit-content-and-return-a-result for more information. [ComposeContentEmitterReturningValues]
          @Composable
          ^
          src/test.kt:8: Error: Composable functions should either emit content into the composition or return a value, but not both.If a composable should offer additional control surfaces to its caller, those control surfaces or callbacks should be provided as parameters to the composable function by the caller.See https://slackhq.github.io/compose-lints/rules/#do-not-emit-content-and-return-a-result for more information. [ComposeContentEmitterReturningValues]
          @Composable
          ^
          2 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors when a Composable function has more than one indirect UI emitter at the top level`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable

        @Composable
        fun Something1() {
            Something2()
        }
        @Composable
        fun Something2() {
            Text("Hola")
            Something3()
        }
        @Composable
        fun Something3() {
            Potato()
        }
        @Composable
        fun Something4() {
            Banana()
        }
        @Composable
        fun Something5() {
            Something3()
            Something4()
        }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:7: Error: Composable functions should either emit content into the composition or return a value, but not both.If a composable should offer additional control surfaces to its caller, those control surfaces or callbacks should be provided as parameters to the composable function by the caller.See https://slackhq.github.io/compose-lints/rules/#do-not-emit-content-and-return-a-result for more information. [ComposeContentEmitterReturningValues]
          @Composable
          ^
          src/test.kt:20: Error: Composable functions should either emit content into the composition or return a value, but not both.If a composable should offer additional control surfaces to its caller, those control surfaces or callbacks should be provided as parameters to the composable function by the caller.See https://slackhq.github.io/compose-lints/rules/#do-not-emit-content-and-return-a-result for more information. [ComposeContentEmitterReturningValues]
          @Composable
          ^
          2 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `make sure to not report twice the same composable`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable

        @Composable
        fun Something() {
            Text("Hi")
            Text("Hola")
            Something2()
        }
        @Composable
        fun Something2() {
            Text("Alo")
        }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:3: Error: Composable functions should either emit content into the composition or return a value, but not both.If a composable should offer additional control surfaces to its caller, those control surfaces or callbacks should be provided as parameters to the composable function by the caller.See https://slackhq.github.io/compose-lints/rules/#do-not-emit-content-and-return-a-result for more information. [ComposeContentEmitterReturningValues]
          @Composable
          ^
          1 errors, 0 warnings
        """
          .trimIndent()
      )
  }
}
