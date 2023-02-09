// Copyright (C) 2023 Slack Technologies, LLC
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ViewModelInjectionDetectorTest(private val viewModel: String) : BaseSlackLintTest() {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "viewModel = {0}")
    fun data(): Collection<Array<String>> {
      return listOf(
        arrayOf("viewModel"),
        arrayOf("weaverViewModel"),
        arrayOf("hiltViewModel"),
        arrayOf("injectedViewModel"),
        arrayOf("mavericksViewModel")
      )
    }
  }

  override fun getDetector(): Detector = ViewModelInjectionDetector()
  override fun getIssues(): List<Issue> = listOf(ViewModelInjectionDetector.ISSUE)

  // This mode is irrelevant to our test and totally untestable with stringy outputs
  override val skipTestModes: Array<TestMode> = arrayOf(TestMode.SUPPRESSIBLE, TestMode.TYPE_ALIAS)

  @Test
  fun `passes when a weaverViewModel is used as a default param`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun MyComposable(
          modifier: Modifier,
          viewModel: MyVM = $viewModel(),
          viewModel2: MyVM = $viewModel(),
        ) { }
      """
        .trimIndent()

    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `overridden functions are ignored`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        override fun Content() {
          val viewModel = $viewModel<MyVM>()
        }
      """
        .trimIndent()

    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `errors when a weaverViewModel is used at the beginning of a Composable`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun MyComposable(modifier: Modifier) {
          val viewModel = $viewModel<MyVM>()
        }

        @Composable
        fun MyComposableNoParams() {
          val viewModel: MyVM = $viewModel()
        }

        @Composable
        fun MyComposableTrailingLambda(block: () -> Unit) {
          val viewModel: MyVM = $viewModel()
        }
      """
        .trimIndent()

    val vmWordUnderline = "~".repeat(viewModel.length)
    lint()
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
            src/test.kt:3: Error: Implicit dependencies of composables should be made explicit.
            Usages of $viewModel to acquire a ViewModel should be done in composable default parameters, so that it is more testable and flexible.
            See https://slackhq.github.io/compose-lints/rules/#viewmodels for more information. [ComposeViewModelInjection]
              val viewModel = $viewModel<MyVM>()
              ~~~~~~~~~~~~~~~~$vmWordUnderline~~~~~~~~
            src/test.kt:8: Error: Implicit dependencies of composables should be made explicit.
            Usages of $viewModel to acquire a ViewModel should be done in composable default parameters, so that it is more testable and flexible.
            See https://slackhq.github.io/compose-lints/rules/#viewmodels for more information. [ComposeViewModelInjection]
              val viewModel: MyVM = $viewModel()
              ~~~~~~~~~~~~~~~~~~~~~~$vmWordUnderline~~
            src/test.kt:13: Error: Implicit dependencies of composables should be made explicit.
            Usages of $viewModel to acquire a ViewModel should be done in composable default parameters, so that it is more testable and flexible.
            See https://slackhq.github.io/compose-lints/rules/#viewmodels for more information. [ComposeViewModelInjection]
              val viewModel: MyVM = $viewModel()
              ~~~~~~~~~~~~~~~~~~~~~~$vmWordUnderline~~
            3 errors, 0 warnings
          """
          .trimIndent()
      )
  }

  @Test
  fun `errors when a weaverViewModel is used in different branches`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun MyComposable(modifier: Modifier) {
          if (blah) {
            val viewModel = $viewModel<MyVM>()
          } else {
            val viewModel: MyOtherVM = $viewModel()
          }
        }
      """
        .trimIndent()

    val vmWordUnderline = "~".repeat(viewModel.length)
    lint()
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
            src/test.kt:4: Error: Implicit dependencies of composables should be made explicit.
            Usages of $viewModel to acquire a ViewModel should be done in composable default parameters, so that it is more testable and flexible.
            See https://slackhq.github.io/compose-lints/rules/#viewmodels for more information. [ComposeViewModelInjection]
                val viewModel = $viewModel<MyVM>()
                ~~~~~~~~~~~~~~~~$vmWordUnderline~~~~~~~~
            src/test.kt:6: Error: Implicit dependencies of composables should be made explicit.
            Usages of $viewModel to acquire a ViewModel should be done in composable default parameters, so that it is more testable and flexible.
            See https://slackhq.github.io/compose-lints/rules/#viewmodels for more information. [ComposeViewModelInjection]
                val viewModel: MyOtherVM = $viewModel()
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~$vmWordUnderline~~
            2 errors, 0 warnings
          """
          .trimIndent()
      )
  }
}
