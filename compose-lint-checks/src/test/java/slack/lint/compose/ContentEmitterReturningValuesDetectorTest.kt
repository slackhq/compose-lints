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
        import androidx.compose.runtime.Composable

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
        import androidx.compose.runtime.Composable

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
  fun `errors when a Composable function has more than one indirect UI emitter at the top level`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Text

        @Composable
        fun Something1() {
            Something2()
        }
        @Composable
        fun Something2(): String {
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
        fun Something5(): String {
            Something3()
            Something4()
        }
        @Composable
        fun Potato() {
            Text("Potato")
        }
        @Composable
        fun Banana() {
            Text("Banana")
        }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:9: Error: Composable functions should either emit content into the composition or return a value, but not both. If a composable should offer additional control surfaces to its caller, those control surfaces or callbacks should be provided as parameters to the composable function by the caller. See https://slackhq.github.io/compose-lints/rules/#do-not-emit-content-and-return-a-result for more information. [ComposeContentEmitterReturningValues]
          fun Something2(): String {
              ~~~~~~~~~~
          src/test.kt:22: Error: Composable functions should either emit content into the composition or return a value, but not both. If a composable should offer additional control surfaces to its caller, those control surfaces or callbacks should be provided as parameters to the composable function by the caller. See https://slackhq.github.io/compose-lints/rules/#do-not-emit-content-and-return-a-result for more information. [ComposeContentEmitterReturningValues]
          fun Something5(): String {
              ~~~~~~~~~~
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
        import androidx.compose.ui.Text

        @Composable
        fun Something(): String {
            Text("Hi")
            Text("Hola")
            Something2()
            return "hi"
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
          src/test.kt:5: Error: Composable functions should either emit content into the composition or return a value, but not both. If a composable should offer additional control surfaces to its caller, those control surfaces or callbacks should be provided as parameters to the composable function by the caller. See https://slackhq.github.io/compose-lints/rules/#do-not-emit-content-and-return-a-result for more information. [ComposeContentEmitterReturningValues]
          fun Something(): String {
              ~~~~~~~~~
          1 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  // https://github.com/slackhq/compose-lints/issues/339
  @Test
  fun `multiple emitters are not a warning in this lint`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable

        @Composable
        fun Test(modifier: Modifier = Modifier) {
            Text(text = "TextOne")
            Text(text = "TextTwo")
        }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }
}
