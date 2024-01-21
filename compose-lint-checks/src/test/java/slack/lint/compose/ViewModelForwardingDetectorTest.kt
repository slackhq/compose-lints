// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class ViewModelForwardingDetectorTest : BaseSlackLintTest() {

  override fun getDetector(): Detector = ViewModelForwardingDetector()

  override fun getIssues(): List<Issue> = listOf(ViewModelForwardingDetector.ISSUE)

  @Test
  fun `allows the forwarding of ViewModels in overridden Composable functions`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable

        @Composable
        override fun Content() {
            val viewModel = weaverViewModel<MyVM>()
            AnotherComposable(viewModel)
        }
        """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `allows the forwarding of ViewModels in interface Composable functions`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      interface MyInterface {
          @Composable
          fun Content() {
              val viewModel = weaverViewModel<MyVM>()
              AnotherComposable(viewModel)
          }
      }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `using state hoisting properly shouldn't be flagged`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable

        @Composable
        fun MyComposable(viewModel: MyViewModel = weaverViewModel()) {
            val state by viewModel.watchAsState()
            AnotherComposable(state, onAvatarClicked = { viewModel(AvatarClickedIntent) })
        }
        """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `errors when a ViewModel is forwarded to another Composable`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable

        class MyViewModel
        
        @Composable
        fun MyComposable(viewModel: MyViewModel) {
            AnotherComposable(viewModel)
        }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/MyViewModel.kt:7: Error: Forwarding a ViewModel through multiple @Composable functions should be avoided. Consider using state hoisting.See https://slackhq.github.io/compose-lints/rules/#hoist-all-the-things for more information. [ComposeViewModelForwarding]
              AnotherComposable(viewModel)
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          1 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `allows the forwarding of ViewModels that are used as keys`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Content() {
          val viewModel = weaverViewModel<MyVM>()
          key(viewModel) { }
          val x = remember(viewModel) { "ABC" }
          LaunchedEffect(viewModel) { }
      }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }
}
