// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class ModifierWithoutDefaultDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = ModifierWithoutDefaultDetector()

  override fun getIssues(): List<Issue> = listOf(ModifierWithoutDefaultDetector.ISSUE)

  @Test
  fun `errors when a Composable has modifiers but without default values`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(modifier: Modifier) { }
      @Composable
      fun Something(modifier: Modifier = Modifier, modifier2: Modifier) { }
    """
        .trimIndent()

    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:5: Error: This @Composable function has a modifier parameter but it doesn't have a default value.See https://slackhq.github.io/compose-lints/rules/#modifiers-should-have-default-parameters for more information. [ComposeModifierWithoutDefault]
          fun Something(modifier: Modifier) { }
                        ~~~~~~~~~~~~~~~~~~
          src/test.kt:7: Error: This @Composable function has a modifier parameter but it doesn't have a default value.See https://slackhq.github.io/compose-lints/rules/#modifiers-should-have-default-parameters for more information. [ComposeModifierWithoutDefault]
          fun Something(modifier: Modifier = Modifier, modifier2: Modifier) { }
                                                       ~~~~~~~~~~~~~~~~~~~
          2 errors, 0 warnings
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
          Autofix for src/test.kt line 5: Add '= Modifier' default value.:
          @@ -5 +5
          - fun Something(modifier: Modifier) { }
          + fun Something(modifier: Modifier = Modifier) { }
          Autofix for src/test.kt line 7: Add '= Modifier' default value.:
          @@ -7 +7
          - fun Something(modifier: Modifier = Modifier, modifier2: Modifier) { }
          + fun Something(modifier: Modifier = Modifier, modifier2: Modifier = Modifier) { }
        """
          .trimIndent()
      )
  }

  @Test
  fun `passes when a Composable inside of an interface has modifiers but without default values`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      interface Bleh {
          @Composable
          fun Something(modifier: Modifier)
      }
      class BlehImpl : Bleh {
          @Composable
          override fun Something(modifier: Modifier) {}
      }
      @Composable
      actual fun Something(modifier: Modifier) {}
    """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `passes when a Composable is an abstract function but without default values`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      abstract class Bleh {
          @Composable
          abstract fun Something(modifier: Modifier)
      }
    """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `passes when a Composable has modifiers with defaults`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun Something(modifier: Modifier = Modifier) {
            Row(modifier = modifier) {
            }
        }
        @Composable
        fun Something(modifier: Modifier = Modifier.fillMaxSize()) {
            Row(modifier = modifier) {
            }
        }
        @Composable
        fun Something(modifier: Modifier = SomeOtherValueFromSomeConstant) {
            Row(modifier = modifier) {
            }
        }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  // https://github.com/slackhq/compose-lints/issues/408
  @Test
  fun `Modifier extensions are fine`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun Modifier.customBackground(
             foo: Foo,
        ): Modifier {
             // compute background based on Foo
        }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }
}
