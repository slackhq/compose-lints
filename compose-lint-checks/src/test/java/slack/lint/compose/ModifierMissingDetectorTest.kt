// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class ModifierMissingDetectorTest : BaseSlackLintTest() {

  override fun getDetector(): Detector = ModifierMissingDetector()

  override fun getIssues(): List<Issue> = listOf(ModifierMissingDetector.ISSUE)

  @Test
  fun `errors when a Composable has a layout inside and it doesn't have a modifier`() {
    @Language("kotlin")
    val code =
      """
          import androidx.compose.ui.Modifier
          import androidx.compose.runtime.Composable

          @Composable
          fun Something1() {
              Row {
              }
          }
          @Composable
          fun Something2() {
              Column(modifier = Modifier.fillMaxSize()) {
              }
          }
          @Composable
          fun Something3(): Unit {
              SomethingElse {
                  Box(modifier = Modifier.fillMaxSize()) {
                  }
              }
          }
          @Composable
          fun Something4(modifier: Modifier = Modifier) {
              Row {
                  Text("Hi!")
              }
          }
      """
        .trimIndent()

    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:5: Error: This @Composable function emits content but doesn't have a modifier parameter.See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeModifierMissing]
          fun Something1() {
              ~~~~~~~~~~
          src/test.kt:10: Error: This @Composable function emits content but doesn't have a modifier parameter.See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeModifierMissing]
          fun Something2() {
              ~~~~~~~~~~
          src/test.kt:15: Error: This @Composable function emits content but doesn't have a modifier parameter.See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeModifierMissing]
          fun Something3(): Unit {
              ~~~~~~~~~~
          3 errors, 0 warnings
        """
          .trimIndent()
      )
    //      .expect(
    //        testMode = TestMode.TYPE_ALIAS,
    //        expectedText =
    //          """
    //          src/test.kt:4: Error: This @Composable function emits content but doesn't have a
    // modifier parameter.See
    // https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for
    // more information. [ComposeModifierMissing]
    //          fun Something1() {
    //              ~~~~~~~~~~
    //          src/test.kt:9: Error: This @Composable function emits content but doesn't have a
    // modifier parameter.See
    // https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for
    // more information. [ComposeModifierMissing]
    //          fun Something2() {
    //              ~~~~~~~~~~
    //          src/test.kt:14: Error: This @Composable function emits content but doesn't have a
    // modifier parameter.See
    // https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for
    // more information. [ComposeModifierMissing]
    //          fun Something3(): TYPE_ALIAS_2 {
    //              ~~~~~~~~~~
    //          3 errors, 0 warnings
    //        """
    //            .trimIndent()
    //      )
  }

  @Test
  fun `errors when a Composable without modifiers has a Composable inside with a modifier`() {
    @Language("kotlin")
    val code =
      """
          import androidx.compose.runtime.Composable

          @Composable
          fun Something1() {
              Whatever(modifier = Modifier.fillMaxSize()) {
              }
          }
          @Composable
          fun Something2(): Unit {
              SomethingElse {
                  Whatever(modifier = Modifier.fillMaxSize()) {
                  }
              }
          }
      """
        .trimIndent()

    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:4: Error: This @Composable function emits content but doesn't have a modifier parameter.See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeModifierMissing]
          fun Something1() {
              ~~~~~~~~~~
          src/test.kt:9: Error: This @Composable function emits content but doesn't have a modifier parameter.See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeModifierMissing]
          fun Something2(): Unit {
              ~~~~~~~~~~
          2 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `non-public visibility Composables are ignored (by default)`() {
    @Language("kotlin")
    val code =
      """
          @Composable
          private fun Something() {
              Row {
              }
          }
          @Composable
          protected fun Something() {
              Column(modifier = Modifier.fillMaxSize()) {
              }
          }
          @Composable
          internal fun Something() {
              SomethingElse {
                  Box(modifier = Modifier.fillMaxSize()) {
                  }
              }
          }
          @Composable
          private fun Something() {
              Whatever(modifier = Modifier.fillMaxSize()) {
              }
          }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `public and internal visibility Composables are checked for 'public_and_internal' configuration`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun Something() {
            Row {
            }
        }
        @Composable
        protected fun Something() {
            Column(modifier = Modifier.fillMaxSize()) {
            }
        }
        @Composable
        internal fun Something() {
            SomethingElse {
                Box(modifier = Modifier.fillMaxSize()) {
                }
            }
        }
        @Composable
        private fun Something() {
            Whatever(modifier = Modifier.fillMaxSize()) {
            }
        }
            """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .configureOption(ModifierMissingDetector.VISIBILITY_THRESHOLD, "public_and_internal")
      .run()
      .expect(
        """
          src/test.kt:5: Error: This @Composable function emits content but doesn't have a modifier parameter.See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeModifierMissing]
          fun Something() {
              ~~~~~~~~~
          src/test.kt:15: Error: This @Composable function emits content but doesn't have a modifier parameter.See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeModifierMissing]
          internal fun Something() {
                       ~~~~~~~~~
          2 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `all Composables are checked for 'all' configuration`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun Something() {
            Row {
            }
        }
        @Composable
        protected fun Something() {
            Column(modifier = Modifier.fillMaxSize()) {
            }
        }
        @Composable
        internal fun Something() {
            SomethingElse {
                Box(modifier = Modifier.fillMaxSize()) {
                }
            }
        }
        @Composable
        private fun Something() {
            Whatever(modifier = Modifier.fillMaxSize()) {
            }
        }
            """
        .trimIndent()

    lint()
      .files(*commonStubs, kotlin(code))
      .configureOption(ModifierMissingDetector.VISIBILITY_THRESHOLD, "all")
      .run()
      .expect(
        """
          src/test.kt:5: Error: This @Composable function emits content but doesn't have a modifier parameter.See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeModifierMissing]
          fun Something() {
              ~~~~~~~~~
          src/test.kt:10: Error: This @Composable function emits content but doesn't have a modifier parameter.See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeModifierMissing]
          protected fun Something() {
                        ~~~~~~~~~
          src/test.kt:15: Error: This @Composable function emits content but doesn't have a modifier parameter.See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeModifierMissing]
          internal fun Something() {
                       ~~~~~~~~~
          src/test.kt:22: Error: This @Composable function emits content but doesn't have a modifier parameter.See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeModifierMissing]
          private fun Something() {
                      ~~~~~~~~~
          4 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `interface Composables are ignored`() {
    @Language("kotlin")
    val code =
      """
          interface MyInterface {
              @Composable
              fun Something() {
                  Row {
                  }
              }

              @Composable
              fun Something() {
                  Column(modifier = Modifier.fillMaxSize()) {
                  }
              }
          }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `overridden Composables are ignored`() {
    @Language("kotlin")
    val code =
      """
          @Composable
          override fun Content() {
              Row {
              }
          }
          @Composable
          override fun TwitterContent() {
              Row {
              }
          }
          @Composable
          override fun ModalContent() {
              Row {
              }
          }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `Composables that return a type that is not Unit shouldn't be processed`() {
    @Language("kotlin")
    val code =
      """
          @Composable
          fun Something(): Int {
              Row {
              }
          }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `Composables with @Preview are ignored`() {
    @Language("kotlin")
    val code =
      """
          import androidx.compose.runtime.Composable
          import androidx.compose.ui.tooling.preview.Preview
          import androidx.compose.ui.Modifier

          fun Modifier.fillMaxSize(): Modifier = TODO()

          @Preview
          @Composable
          fun Something() {
              Row {
              }
          }
          @Preview
          @Composable
          fun Something() {
              Column(modifier = Modifier.fillMaxSize()) {
              }
          }
          @Preview
          @Composable
          fun Something(): Unit {
              SomethingElse {
                  Box(modifier = Modifier.fillMaxSize()) {
                  }
              }
          }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `non content emitting root composables are ignored`() {
    @Language("kotlin")
    val code =
      """
          import androidx.compose.runtime.Composable

          @Composable
          fun MyDialog() {
            AlertDialog(
              onDismissRequest = { /*TODO*/ },
              buttons = { Text(text = "Button") },
              text = { Text(text = "Body") },
            )
          }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `non content emitter with content emitter not ignored`() {
    @Language("kotlin")
    val code =
      """
          import androidx.compose.runtime.Composable

          @Composable
          fun MyDialog() {
            Text(text = "Unicorn")

            AlertDialog(
              onDismissRequest = { /*TODO*/ },
              buttons = { Text(text = "Button") },
              text = { Text(text = "Body") },
            )
          }
      """
        .trimIndent()

    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:4: Error: This @Composable function emits content but doesn't have a modifier parameter.See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeModifierMissing]
          fun MyDialog() {
              ~~~~~~~~
          1 errors, 0 warnings
        """
          .trimIndent()
      )
  }
}
