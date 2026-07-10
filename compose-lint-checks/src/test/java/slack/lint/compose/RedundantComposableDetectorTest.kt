// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class RedundantComposableDetectorTest : BaseComposeLintTest() {

  // Self-contained stubs: the callable composables are exempt from the rule (Text is `external` so
  // it has no body; CompositionLocal.current is an interface member), so the stubs produce no
  // warnings.
  private val stubs =
    kotlin(
      """
      package androidx.compose.runtime

      annotation class Composable

      annotation class ReadOnlyComposable

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

      fun <T> derivedStateOf(calculation: () -> T): State<T> = error("stub")

      @Suppress("ComposeRedundantComposable")
      @Composable
      inline fun <T> remember(key1: Any?, crossinline calculation: () -> T): T = error("stub")

      @Composable external fun Text(text: String)
      """
        .trimIndent()
    )

  private val unitStubs =
    kotlin(
      """
      package androidx.compose.ui.unit

      class Dp(val value: Int)

      val Int.dp: Dp
        get() = Dp(this)
      """
        .trimIndent()
    )

  private val issue574Stubs =
    arrayOf(
      kotlin(
        """
        package androidx.compose.ui

        interface Modifier {
          companion object : Modifier
        }

        object Alignment {
          val CenterVertically: Any = Any()
        }
        """
          .trimIndent()
      ),
      kotlin(
        """
        package androidx.compose.foundation.layout

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.unit.Dp

        @Composable
        external fun Row(
          modifier: Modifier = Modifier,
          verticalAlignment: Any? = null,
          content: @Composable () -> Unit,
        )

        @Composable external fun Spacer(modifier: Modifier = Modifier)

        fun Modifier.padding(end: Dp): Modifier = this
        """
          .trimIndent()
      ),
    )

  override fun getDetector(): Detector = RedundantComposableDetector()

  override fun getIssues(): List<Issue> = RedundantComposableDetector.ISSUES.toList()

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
        src/test.kt:3: Warning: This declaration is annotated with @Composable but doesn't call any other @Composable functions or read any @Composable properties (like a CompositionLocal's current), so it doesn't use the composition and the @Composable annotation can be removed.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeRedundantComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:6: Warning: This declaration is annotated with @Composable but doesn't call any other @Composable functions or read any @Composable properties (like a CompositionLocal's current), so it doesn't use the composition and the @Composable annotation can be removed.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeRedundantComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:12: Warning: This declaration is annotated with @Composable but doesn't call any other @Composable functions or read any @Composable properties (like a CompositionLocal's current), so it doesn't use the composition and the @Composable annotation can be removed.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeRedundantComposable]
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
  fun `informational when only CompositionLocals are read`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.CompositionLocal
      import androidx.compose.runtime.compositionLocalOf

      val LocalThing: CompositionLocal<Int> = compositionLocalOf { 0 }

      @Composable
      fun readsCompositionLocal() {
        println(LocalThing.current)
      }

      val themed: Int
        @Composable get() = LocalThing.current
      """
        .trimIndent()
    lint()
      .files(stubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:7: Hint: This declaration only uses the composition to read CompositionLocal values, so it can be annotated with @ReadOnlyComposable to avoid generating a group around its body.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeReadOnlyComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:13: Hint: This declaration only uses the composition to read CompositionLocal values, so it can be annotated with @ReadOnlyComposable to avoid generating a group around its body.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeReadOnlyComposable]
          @Composable get() = LocalThing.current
          ~~~~~~~~~~~
        0 errors, 0 warnings, 2 hints
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
        Autofix for src/test.kt line 7: Annotate with @ReadOnlyComposable:
        @@ -2,0 +3 @@
        +import androidx.compose.runtime.ReadOnlyComposable
        @@ -7,0 +9 @@
        +@ReadOnlyComposable
        Autofix for src/test.kt line 13: Annotate with @ReadOnlyComposable:
        @@ -2,0 +3 @@
        +import androidx.compose.runtime.ReadOnlyComposable
        @@ -13 +14,2 @@
        -  @Composable get() = LocalThing.current
        +  @Composable
        +  @ReadOnlyComposable get() = LocalThing.current
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
      fun readsCompositionLocalAndCallsComposable() {
        println(LocalThing.current)
        Text("hi")
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors when inline composable call is used`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.State
      import androidx.compose.runtime.derivedStateOf
      import androidx.compose.runtime.remember
      import androidx.compose.ui.unit.Dp
      import androidx.compose.ui.unit.dp

      enum class SampleState {
        ONE,
        TWO,
      }

      @Composable
      fun rememberCollapsed(
        state: SampleState,
        contentHeight: Dp,
      ): State<Dp> {
        return remember(contentHeight) {
          derivedStateOf {
            when (state) {
              SampleState.ONE -> 84.dp
              SampleState.TWO -> 88.dp
            }
          }
        }
      }
      """
        .trimIndent()

    lint()
      .files(stubs, unitStubs, kotlin(code).to("RememberCollapsed.kt"))
      .isolated("src/RememberCollapsed.kt")
      .run()
      .expectClean()
  }

  // Exact IDE-only fixture from #574. The local-call test below is the unit-host negative control
  // for the same UAST resolution fallback.
  @Test
  fun `issue 574 ProgressSlider is not redundant`() {
    @Language("kotlin")
    val code =
      """
      package com.example.myapplication

      import androidx.compose.foundation.layout.Row
      import androidx.compose.foundation.layout.Spacer
      import androidx.compose.foundation.layout.padding
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Alignment
      import androidx.compose.ui.Modifier
      import androidx.compose.ui.unit.dp

      @Composable
      internal fun ProgressSlider(
        stepsCount: Int,
        modifier: Modifier = Modifier,
      ) {
        Row(
          modifier = modifier,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          for (i in 0 until stepsCount) {
            Spacer(
              modifier = Modifier.padding(end = if (i < stepsCount - 1) 24.dp else 0.dp),
            )
          }
        }
      }
      """
        .trimIndent()

    lint()
      .files(stubs, unitStubs, *issue574Stubs, kotlin(code).to("ProgressSlider.kt"))
      .isolated("src/ProgressSlider.kt")
      .run()
      .expectClean()
  }

  @Test
  fun `no errors when UAST cannot resolve a local composable call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun outer() {
        @Composable
        fun local() {
          println("local")
        }

        local()
      }
      """
        .trimIndent()

    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors when already annotated with ReadOnlyComposable`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.CompositionLocal
      import androidx.compose.runtime.ReadOnlyComposable
      import androidx.compose.runtime.compositionLocalOf

      val LocalThing: CompositionLocal<Int> = compositionLocalOf { 0 }

      @Composable
      @ReadOnlyComposable
      fun readsCompositionLocal() {
        println(LocalThing.current)
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors when composition is only used in a default argument value`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.CompositionLocal
      import androidx.compose.runtime.ReadOnlyComposable
      import androidx.compose.runtime.compositionLocalOf

      val LocalThing: CompositionLocal<Int> = compositionLocalOf { 0 }

      // A @Composable function, used below as a default argument value.
      @Composable
      @ReadOnlyComposable
      fun provideValue(): Int = LocalThing.current

      // The bodies use no composition, but each default value invokes the composition (a @Composable
      // function call / a @Composable property read), so the @Composable annotation is required:
      // removing it would be a compile error. The rule only inspects the body and so must not flag these.
      @Composable
      fun usesComposableFunctionDefault(value: Int = provideValue()): Int = value

      @Composable
      fun usesComposablePropertyDefault(value: Int = LocalThing.current): Int = value
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
  fun `no errors for composables that take typealiased composable slot`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      typealias ComposableTypealias = @Composable () -> Unit

      @Composable
      fun Wrapper(content: ComposableTypealias) {
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

  @Test
  fun `no errors when invoking a composable lambda stored in a property`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      sealed interface State {
        data object A : State
        class B(val composable: @Composable () -> Unit) : State
      }

      @Composable
      fun HandleState(state: State) {
        when (state) {
          State.A -> Unit
          is State.B -> state.composable()
        }
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }
}
