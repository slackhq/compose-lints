// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertFalse
import org.junit.Test

class UnstableReceiverDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = UnstableReceiverDetector()

  override fun getIssues(): List<Issue> = listOf(UnstableReceiverDetector.ISSUE)

  private val enabledIssueConfig =
    xml(
      "lint.xml",
      """
      <lint>
        <issue id="ComposeUnstableReceiver" severity="warning" />
      </lint>
      """
        .trimIndent(),
    )

  @Test
  fun `issue is disabled by default`() {
    assertFalse(UnstableReceiverDetector.ISSUE.isEnabledByDefault())
  }

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

      // Supertypes
      @Stable
      interface Presenter<T> {
        @Composable fun present(): T
      }

      class HomePresenter : Presenter<String> {
        @Composable override fun present(): String { return "hi" }
      }
      """
        .trimIndent()
    lint().files(enabledIssueConfig, *commonStubs, kotlin(code)).run().expectClean()
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

      // Supertypes
      interface Presenter {
        @Composable fun present()
      }

      class HomePresenter : Presenter {
        @Composable override fun present() { println("hi") }
      }
      """
        .trimIndent()
    lint()
      .files(enabledIssueConfig, *commonStubs, kotlin(code))
      .run()
      .expect(
        """
        src/ExampleInterface.kt:4: Warning: Instance composable functions on non-stable classes will always be recomposed. If possible, make the receiver type stable or refactor this function if that isn't possible.

        See https://slackhq.github.io/compose-lints/rules/#unstable-receivers for more information. [ComposeUnstableReceiver]
          @Composable fun Content()
                          ~~~~~~~
        src/ExampleInterface.kt:8: Warning: Instance composable functions on non-stable classes will always be recomposed. If possible, make the receiver type stable or refactor this function if that isn't possible.

        See https://slackhq.github.io/compose-lints/rules/#unstable-receivers for more information. [ComposeUnstableReceiver]
          @Composable fun Content() {}
                          ~~~~~~~
        src/ExampleInterface.kt:12: Warning: Instance composable functions on non-stable classes will always be recomposed. If possible, make the receiver type stable or refactor this function if that isn't possible.

        See https://slackhq.github.io/compose-lints/rules/#unstable-receivers for more information. [ComposeUnstableReceiver]
        fun Example.OtherContent() {}
            ~~~~~~~
        src/ExampleInterface.kt:16: Warning: Instance composable functions on non-stable classes will always be recomposed. If possible, make the receiver type stable or refactor this function if that isn't possible.

        See https://slackhq.github.io/compose-lints/rules/#unstable-receivers for more information. [ComposeUnstableReceiver]
          @Composable fun present()
                          ~~~~~~~
        src/ExampleInterface.kt:20: Warning: Instance composable functions on non-stable classes will always be recomposed. If possible, make the receiver type stable or refactor this function if that isn't possible.

        See https://slackhq.github.io/compose-lints/rules/#unstable-receivers for more information. [ComposeUnstableReceiver]
          @Composable override fun present() { println("hi") }
                                   ~~~~~~~
        0 errors, 5 warnings
        """
          .trimIndent()
      )
  }

  // https://github.com/slackhq/compose-lints/issues/326
  @Test
  fun `composable property getter with non-Unit return on value class receiver reports no errors`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.Immutable

      @Immutable
      @kotlin.jvm.JvmInline
      value class Color(val value: ULong) {
        fun copy(alpha: Float): Color = this
      }

      public val Color.transparent: Color
          @Composable get() = this.copy(alpha = 0f)
      """
        .trimIndent()
    lint().files(enabledIssueConfig, *commonStubs, kotlin(code)).run().expectClean()
  }

  // Regression for the value-class receiver path: K2 UAST doesn't surface the receiver of a
  // value-class extension as a uast parameter, and (for property getters) treats the declaration as
  // non-top-level so its containing file facade (*Kt) is checked as the "containing class". Neither
  // the missing receiver param nor the facade should be treated as an unstable receiver. The getter
  // forms below are the ones that previously false-positived.
  @Test
  fun `composable extensions on value class receivers report no errors`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.Immutable

      @Immutable
      @kotlin.jvm.JvmInline
      value class Color(val value: ULong)

      // Underlying type is stable even without an explicit stability annotation.
      @kotlin.jvm.JvmInline
      value class Dollars(val amount: Int)

      @Composable
      fun Color.Render() {}

      @Composable
      fun Dollars.Render() {}

      val Color.rendered: Unit
          @Composable get() {}

      val Dollars.rendered: Unit
          @Composable get() {}
      """
        .trimIndent()
    lint().files(enabledIssueConfig, *commonStubs, kotlin(code)).run().expectClean()
  }

  // A value/inline class is stable iff its underlying property type is stable, so composable
  // members
  // of one (whose containing class is the receiver) shouldn't be flagged.
  @Test
  fun `composable members of value classes report no errors`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.Immutable

      @kotlin.jvm.JvmInline
      value class Dollars(val amount: Int) {
        @Composable fun Render() {}
      }

      @Immutable
      @kotlin.jvm.JvmInline
      value class Color(val value: ULong) {
        @Composable fun Render() {}
      }
      """
        .trimIndent()
    lint().files(enabledIssueConfig, *commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `unstable receiver types with non-Unit return type report no errors`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      interface ExampleInterface {
        @Composable fun Content(): String
      }

      class Example {
        @Composable fun Content(): String { return "hi" }
      }

      @Composable
      fun Example.OtherContent(): String { return "hi" }

      @get:Composable
      val Example.OtherContentProperty get() {}

      // Supertypes
      interface Presenter<T> {
        @Composable fun present(): T
      }

      class HomePresenter : Presenter<String> {
        @Composable override fun present(): String { return "hi" }
      }
      """
        .trimIndent()
    lint().files(enabledIssueConfig, *commonStubs, kotlin(code)).run().expectClean()
  }
}
