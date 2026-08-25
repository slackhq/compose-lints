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

      import kotlin.reflect.KProperty

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

      operator fun <T> State<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value

      fun <T> derivedStateOf(calculation: () -> T): State<T> = error("stub")

      @Suppress("ComposeRedundantComposable")
      @Composable
      inline fun <T> remember(key1: Any?, crossinline calculation: () -> T): T = error("stub")

      @Composable external fun Text(text: String)
      """
        .trimIndent()
    )

  private val annotationStubs =
    kotlin(
      """
      package example

      @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
      annotation class MyAnnotation

      @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
      annotation class MyOtherAnnotation

      @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
      annotation class NotComposable
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
        src/test.kt:7: Hint: All composable functions and properties used by this declaration are marked @ReadOnlyComposable, so this declaration can be marked @ReadOnlyComposable too.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeReadOnlyComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:13: Hint: All composable functions and properties used by this declaration are marked @ReadOnlyComposable, so this declaration can be marked @ReadOnlyComposable too.

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
  fun `informational when only read-only composable functions and properties are used`() {
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
      fun readOnlyValue(): Int = LocalThing.current

      val readOnlyProperty: Int
        @Composable
        @ReadOnlyComposable
        get() = LocalThing.current

      @Composable
      fun delegatesToReadOnlyFunction() {
        println(readOnlyValue())
      }

      @Composable
      fun readsReadOnlyProperty() {
        println(readOnlyProperty)
      }
      """
        .trimIndent()

    lint()
      .files(stubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:17: Hint: All composable functions and properties used by this declaration are marked @ReadOnlyComposable, so this declaration can be marked @ReadOnlyComposable too.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeReadOnlyComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:22: Hint: All composable functions and properties used by this declaration are marked @ReadOnlyComposable, so this declaration can be marked @ReadOnlyComposable too.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeReadOnlyComposable]
        @Composable
        ~~~~~~~~~~~
        0 errors, 0 warnings, 2 hints
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
        Autofix for src/test.kt line 17: Annotate with @ReadOnlyComposable:
        @@ -17,0 +18 @@
        +@ReadOnlyComposable
        Autofix for src/test.kt line 22: Annotate with @ReadOnlyComposable:
        @@ -22,0 +23 @@
        +@ReadOnlyComposable
        """
          .trimIndent()
      )
  }

  @Test
  fun `informational for expect read-only composable property getter`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.ReadOnlyComposable

      expect class ProvidableCompositionLocal<T> {
        val current: T
          @ReadOnlyComposable @Composable get
      }

      expect val LocalAnsweringNavigatorProvider: ProvidableCompositionLocal<Any?>

      @Composable
      fun answeringNavigationAvailable(): Boolean =
        LocalAnsweringNavigatorProvider.current != null
      """
        .trimIndent()

    // Standalone expect declarations model a commonMain dependency in the lint unit harness.
    lint()
      .files(stubs, kotlin(code))
      .allowCompilationErrors(true)
      .run()
      .expect(
        """
        src/ProvidableCompositionLocal.kt:11: Hint: All composable functions and properties used by this declaration are marked @ReadOnlyComposable, so this declaration can be marked @ReadOnlyComposable too.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeReadOnlyComposable]
        @Composable
        ~~~~~~~~~~~
        0 errors, 0 warnings, 1 hint
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
      import androidx.compose.runtime.ReadOnlyComposable
      import androidx.compose.runtime.compositionLocalOf
      import androidx.compose.runtime.Text

      val LocalThing: CompositionLocal<Int> = compositionLocalOf { 0 }

      @Composable
      @ReadOnlyComposable
      fun readOnlyValue(): Int = LocalThing.current

      @Composable
      fun callsComposable() {
        Text("hi")
      }

      @Composable
      fun readsCompositionLocalAndCallsComposable() {
        println(readOnlyValue())
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
  fun `informational when UAST cannot resolve a local read-only composable call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.ReadOnlyComposable

      @Composable
      fun outer() {
        @Suppress("ComposeRedundantComposable")
        @Composable
        @ReadOnlyComposable
        fun local(): Int = 1

        println(local())
      }
      """
        .trimIndent()

    lint()
      .files(stubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:4: Hint: All composable functions and properties used by this declaration are marked @ReadOnlyComposable, so this declaration can be marked @ReadOnlyComposable too.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeReadOnlyComposable]
        @Composable
        ~~~~~~~~~~~
        0 errors, 0 warnings, 1 hint
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
        Autofix for src/test.kt line 4: Annotate with @ReadOnlyComposable:
        @@ -4,0 +5 @@
        +@ReadOnlyComposable
        """
          .trimIndent()
      )
  }

  @Test
  fun `ReadOnlyComposable without Composable does not count as composition usage`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.ReadOnlyComposable

      @ReadOnlyComposable
      fun ordinaryValue(): Int = 1

      @Composable
      fun callsOrdinaryValue() {
        println(ordinaryValue())
      }
      """
        .trimIndent()

    lint()
      .files(stubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:7: Warning: This declaration is annotated with @Composable but doesn't call any other @Composable functions or read any @Composable properties (like a CompositionLocal's current), so it doesn't use the composition and the @Composable annotation can be removed.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeRedundantComposable]
        @Composable
        ~~~~~~~~~~~
        0 errors, 1 warnings
        """
          .trimIndent()
      )
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
  fun `no errors when reading delegated Compose State`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.MutableState
      import androidx.compose.runtime.State
      import androidx.compose.runtime.getValue

      @Composable
      fun readsState(state: State<Int>) {
        val value by state
        println(value)
      }

      @Composable
      fun readsMutableState(state: MutableState<Int>) {
        val value by state
        println(value)
      }
      """
        .trimIndent()

    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `ordinary property delegates do not count as Compose State`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import kotlin.reflect.KProperty

      class OrdinaryDelegate {
        operator fun getValue(thisObj: Any?, property: KProperty<*>): Int = 1
      }

      @Composable
      fun readsOrdinaryDelegate(delegate: OrdinaryDelegate) {
        val value by delegate
        println(value)
      }
      """
        .trimIndent()

    lint().files(stubs, kotlin(code)).run().expectWarningCount(1)
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
  fun `no errors for nullable and parenthesized composable slots`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun nullableSlot(content: (@Composable () -> Unit)?) {
        println("doesn't even call content")
      }

      @Composable
      fun parenthesizedSlot(content: (@Composable () -> Unit)) {
        println("doesn't even call content")
      }

      @Composable
      fun nestedNullableSlot(content: ((@Composable () -> Unit)?)?) {
        println("doesn't even call content")
      }

      typealias NullableComposableSlot = (@Composable () -> Unit)?

      @Composable
      fun nullableTypealiasSlot(content: NullableComposableSlot) {
        println("doesn't even call content")
      }
      """
        .trimIndent()

    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `ordinary nullable lambdas and unrelated annotations are not composable slots`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import example.NotComposable

      @Composable
      fun ordinaryNullableSlot(content: (() -> Unit)?) {
        println("doesn't even call content")
      }

      @Composable
      fun unrelatedAnnotatedSlot(content: (@NotComposable () -> Unit)?) {
        println("doesn't even call content")
      }
      """
        .trimIndent()

    lint().files(stubs, annotationStubs, kotlin(code)).run().expectWarningCount(2)
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

  @Test
  fun `no errors when reading top-level and member composable properties`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.Text

      val topLevelContent: Int
        @Composable get() {
          Text("top-level")
          return 1
        }

      class ContentHolder {
        val memberContent: Int
          @Composable get() {
            Text("member")
            return 2
          }
      }

      @Composable
      fun readsTopLevelProperty() {
        println(topLevelContent)
      }

      @Composable
      fun readsMemberProperty(holder: ContentHolder) {
        println(holder.memberContent)
      }
      """
        .trimIndent()

    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `ordinary property getters do not count as composition usage`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      val ordinaryProperty: Int
        get() = 1

      @Composable
      fun readsOrdinaryProperty() {
        println(ordinaryProperty)
      }
      """
        .trimIndent()

    lint().files(stubs, kotlin(code)).run().expectWarningCount(1)
  }

  @Test
  fun `configured annotations only ignore matching redundant composables`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import example.MyAnnotation
      import example.MyOtherAnnotation
      import example.NotComposable

      @MyAnnotation
      @Composable
      fun annotated() {
        println("annotated")
      }

      @MyOtherAnnotation
      @Composable
      fun alsoAnnotated() {
        println("also annotated")
      }

      @NotComposable
      @Composable
      fun unconfigured() {
        println("unconfigured")
      }

      @Composable
      fun ordinary() {
        println("ordinary")
      }

      @MyAnnotation
      object AnnotatedEntryPoint {
        @Composable
        operator fun invoke() {
          println("annotated entry point")
        }

        @Composable
        fun unrelated() {
          println("ordinary member")
        }
      }

      @MyAnnotation
      class AnnotatedClass {
        @Composable
        operator fun invoke() {
          println("ordinary class member")
        }
      }

      @MyAnnotation
      object ValueReturningEntryPoint {
        @Composable
        operator fun invoke(): String = "ordinary value-returning member"
      }
      """
        .trimIndent()

    val configuration =
      xml(
        "lint.xml",
        """
        <lint>
          <issue id="ComposeRedundantComposable">
            <option
              name="ignore-annotated"
              value="example.MyAnnotation,example.MyOtherAnnotation" />
          </issue>
        </lint>
        """
          .trimIndent(),
      )

    lint().files(stubs, annotationStubs, kotlin(code), configuration).run().expectWarningCount(5)
  }

  @Test
  fun `ignored annotations do not suppress read-only composable suggestions`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.CompositionLocal
      import androidx.compose.runtime.compositionLocalOf
      import example.MyAnnotation

      val LocalThing: CompositionLocal<Int> = compositionLocalOf { 0 }

      @MyAnnotation
      @Composable
      fun readsCompositionLocal() {
        println(LocalThing.current)
      }
      """
        .trimIndent()

    val configuration =
      xml(
        "lint.xml",
        """
        <lint>
          <issue id="ComposeRedundantComposable">
            <option name="ignore-annotated" value="example.MyAnnotation" />
          </issue>
        </lint>
        """
          .trimIndent(),
      )

    lint()
      .files(stubs, annotationStubs, kotlin(code), configuration)
      .run()
      .expect(
        """
        src/test.kt:9: Hint: All composable functions and properties used by this declaration are marked @ReadOnlyComposable, so this declaration can be marked @ReadOnlyComposable too.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeReadOnlyComposable]
        @Composable
        ~~~~~~~~~~~
        0 errors, 0 warnings, 1 hint
        """
          .trimIndent()
      )
  }
}
