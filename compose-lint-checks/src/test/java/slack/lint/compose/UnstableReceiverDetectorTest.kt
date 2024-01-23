// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class UnstableReceiverDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = UnstableReceiverDetector()

  override fun getIssues(): List<Issue> = listOf(UnstableReceiverDetector.ISSUE)

  @Test
  fun `stable receiver types report no errors`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.Stable
        import androidx.compose.runtime.StableMarker

        @StableMarker
        annotation class CustomStable

        @Stable
        interface ExampleInterface {
          @Composable fun Content()
        }

        @Stable
        class Example {
          @Composable fun Content() {}
        }

        @CustomStable
        class CustomExample {
          @Composable fun Content() {}
        }

        @Composable
        fun Example.OtherContent() {}

        @get:Composable
        val Example.OtherContentProperty get() {}

        @Stable
        enum class EnumExample {
          TEST;
          @Composable fun Content() {}
        }

        @Composable
        fun EnumExample.OtherContent() {}

        @Stable
        enum class EnumExample {
          TEST;
          @Composable fun Content() {}
        }

        @Composable
        fun EnumExample.OtherContent() {}

        // Primitives are ok
        @Composable
        fun String.OtherContent() {}
        @Composable
        fun Int.OtherContent() {}

        // Functions are ok
        @Composable
        fun (() -> Unit).OtherContent() {}
        @Composable
        fun Function<String>.OtherContent() {}
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `unstable receiver types report errors`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable

        interface ExampleInterface {
          @Composable fun Content()
        }

        class Example {
          @Composable fun Content() {}
        }

        @Composable
        fun Example.OtherContent() {}

        @get:Composable
        val Example.OtherContentProperty get() {}
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/ExampleInterface.kt:4: Warning: Instance composable functions on non-stable classes will always be recomposed. If possible, make the receiver type stable or refactor this function if that isn't possible. See https://slackhq.github.io/compose-lints/rules/#unstable-receivers for more information. [ComposeUnstableReceiver]
            @Composable fun Content()
                            ~~~~~~~
          src/ExampleInterface.kt:8: Warning: Instance composable functions on non-stable classes will always be recomposed. If possible, make the receiver type stable or refactor this function if that isn't possible. See https://slackhq.github.io/compose-lints/rules/#unstable-receivers for more information. [ComposeUnstableReceiver]
            @Composable fun Content() {}
                            ~~~~~~~
          src/ExampleInterface.kt:12: Warning: Instance composable functions on non-stable classes will always be recomposed. If possible, make the receiver type stable or refactor this function if that isn't possible. See https://slackhq.github.io/compose-lints/rules/#unstable-receivers for more information. [ComposeUnstableReceiver]
          fun Example.OtherContent() {}
              ~~~~~~~
          src/ExampleInterface.kt:15: Warning: Instance composable functions on non-stable classes will always be recomposed. If possible, make the receiver type stable or refactor this function if that isn't possible. See https://slackhq.github.io/compose-lints/rules/#unstable-receivers for more information. [ComposeUnstableReceiver]
          val Example.OtherContentProperty get() {}
              ~~~~~~~
          0 errors, 4 warnings
        """
          .trimIndent()
      )
  }
}
