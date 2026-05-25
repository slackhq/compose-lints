// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class RedundantComposableDetectorTest : BaseComposeLintTest() {

  // Self-contained stubs: the callable composables are exempt from the rule (Text is `external` so
  // it
  // has no body; CompositionLocal.current is an interface member), so the stubs produce no
  // warnings.
  private val stubs =
    kotlin(
      """
      package androidx.compose.runtime

      annotation class Composable

      interface CompositionLocal<T> {
        val current: T
          @Composable get() = error("stub")
      }

      fun <T> compositionLocalOf(defaultFactory: () -> T): CompositionLocal<T> = error("stub")

      interface State<out T> {
        val value: T
      }

      interface MutableState<T> : State<T> {
        override var value: T
      }

      @Composable external fun Text(text: String)
      """
        .trimIndent()
    )

  override fun getDetector(): Detector = RedundantComposableDetector()

  override fun getIssues(): List<Issue> = listOf(RedundantComposableDetector.ISSUE)

  @Test
  fun `errors when a composable does not use composition`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun redundant() = println("derp")

      @Composable
      fun stillRedundant(name: String) {
        println(name.length)
      }

      val redundantProperty: Int
        @Composable get() = 3
      """
        .trimIndent()
    lint()
      .files(stubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:3: Warning: This declaration is annotated with @Composable but doesn't call any other @Composable functions or read any @Composable properties (like a CompositionLocal's current), so it doesn't use the composition and the @Composable annotation can be removed. See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeRedundantComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:6: Warning: This declaration is annotated with @Composable but doesn't call any other @Composable functions or read any @Composable properties (like a CompositionLocal's current), so it doesn't use the composition and the @Composable annotation can be removed. See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeRedundantComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:12: Warning: This declaration is annotated with @Composable but doesn't call any other @Composable functions or read any @Composable properties (like a CompositionLocal's current), so it doesn't use the composition and the @Composable annotation can be removed. See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeRedundantComposable]
          @Composable get() = 3
          ~~~~~~~~~~~
        0 errors, 3 warnings
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
        Autofix for src/test.kt line 3: Remove redundant @Composable:
        @@ -3 +2,0 @@
        -@Composable
        Autofix for src/test.kt line 6: Remove redundant @Composable:
        @@ -6 +5,0 @@
        -@Composable
        Autofix for src/test.kt line 12: Remove redundant @Composable:
        @@ -12 +12 @@
        -  @Composable get() = 3
        +  get() = 3
        """
          .trimIndent()
      )
  }

  @Test
  fun `no errors when composition is used`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.CompositionLocal
      import androidx.compose.runtime.compositionLocalOf
      import androidx.compose.runtime.Text

      val LocalThing: CompositionLocal<Int> = compositionLocalOf { 0 }

      @Composable
      fun callsComposable() {
        Text("hi")
      }

      @Composable
      fun readsCompositionLocal() {
        println(LocalThing.current)
      }

      val themed: Int
        @Composable get() = LocalThing.current
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors when reading or writing State value`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.State
      import androidx.compose.runtime.MutableState

      @Composable
      fun readsState(state: State<Int>): Int {
        return state.value
      }

      @Composable
      fun writesState(state: MutableState<Int>) {
        state.value = 5
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors for composables that take a composable slot`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Wrapper(content: @Composable () -> Unit) {
        println("doesn't even call content")
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors for overrides and overridable declarations`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      interface Screen {
        @Composable fun Content()
      }

      class Home : Screen {
        @Composable override fun Content() {
          println("nothing composable here")
        }
      }

      abstract class Base {
        @Composable open fun Render() {
          println("nothing composable here")
        }
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }
}
