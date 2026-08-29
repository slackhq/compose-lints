// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestFiles.binaryStub
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class NonRestartableComposableDetectorTest : BaseComposeLintTest() {

  private val compiledStubs =
    binaryStub(
      "libs/compiled-stubs.jar",
      kotlin(
          """
          package androidx.compose.runtime

          @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.TYPE)
          @Retention(AnnotationRetention.BINARY)
          annotation class Composable

          @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
          @Retention(AnnotationRetention.SOURCE)
          annotation class NonRestartableComposable

          @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
          @Retention(AnnotationRetention.SOURCE)
          annotation class NonSkippableComposable

          @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
          @Retention(AnnotationRetention.BINARY)
          annotation class ReadOnlyComposable

          @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
          @Retention(AnnotationRetention.SOURCE)
          annotation class ExplicitGroupsComposable

          interface State<out T> {
            val value: T
          }

          @Composable
          external fun Content(value: String, child: @Composable () -> Unit = {})

          @Composable
          external fun VarargContent(vararg values: String)

          @Composable
          external fun ValueContent(): String

          @Composable
          external fun DefaultValue(): String

          @Composable
          @ReadOnlyComposable
          external fun ReadOnlyContent()

          @Composable
          @NonRestartableComposable
          external fun NonRestartableContent()

          @Composable
          @NonSkippableComposable
          external fun NonSkippableContent()

          class CompiledContent {
            @Composable
            @NonRestartableComposable
            fun render(value: String) {}
          }
          """
        )
        .indented()
        .to("src/androidx/compose/runtime/CompiledStubs.kt"),
      kotlin(
          """
          package androidx.compose.ui.tooling.preview

          annotation class Preview
          """
        )
        .indented()
        .to("src/androidx/compose/ui/tooling/preview/Preview.kt"),
    )

  override fun getDetector(): Detector = NonRestartableComposableDetector()

  override fun getIssues(): List<Issue> = listOf(NonRestartableComposableDetector.ISSUE)

  override fun lint(): TestLintTask = super.lint().allowKotlinClassStubs(true)

  @Test
  fun `suggests non-restartable for a direct composable call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.Content

      @Composable
      fun Wrapper(value: String) = (((Content(((value))))))
      """
        .trimIndent()

    lint()
      .files(compiledStubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:4: Hint: This composable's body only delegates to another composable. Consider marking it @NonRestartableComposable when its own restart and skip boundary is unlikely to be useful. The annotation avoids generating that code, but also prevents the wrapper from being restarted or skipped independently.

        See https://slackhq.github.io/compose-lints/rules/#avoid-unnecessary-restart-groups for more information. [ComposeNonRestartableComposable]
        @Composable
        ~~~~~~~~~~~
        0 errors, 0 warnings, 1 hint
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/test.kt line 4: Annotate with @NonRestartableComposable:
        @@ -2,0 +3 @@
        +import androidx.compose.runtime.NonRestartableComposable
        @@ -4,0 +6 @@
        +@NonRestartableComposable
        """
          .trimIndent()
      )
  }

  @Test
  fun `suggests non-restartable for a one-call block body with dynamic defaults`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.Content
      import androidx.compose.runtime.DefaultValue

      @Composable
      fun Wrapper(
        value: String = DefaultValue(),
        child: @Composable () -> Unit,
      ) {
        Content(value = value, child = child)
      }
      """
        .trimIndent()

    lint()
      .files(compiledStubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:5: Hint: This composable's body only delegates to another composable. Consider marking it @NonRestartableComposable when its own restart and skip boundary is unlikely to be useful. The annotation avoids generating that code, but also prevents the wrapper from being restarted or skipped independently.

        See https://slackhq.github.io/compose-lints/rules/#avoid-unnecessary-restart-groups for more information. [ComposeNonRestartableComposable]
        @Composable
        ~~~~~~~~~~~
        0 errors, 0 warnings, 1 hint
        """
          .trimIndent()
      )
  }

  @Test
  fun `suggests non-restartable for a forwarded extension receiver`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      class Host

      @Composable
      fun Host.Content(value: String) {}

      @Composable
      fun Wrapper(host: Host, value: String) = host.Content(value)
      """
        .trimIndent()

    lint()
      .files(compiledStubs, kotlin(code))
      .run()
      .expect(
        """
        src/Host.kt:8: Hint: This composable's body only delegates to another composable. Consider marking it @NonRestartableComposable when its own restart and skip boundary is unlikely to be useful. The annotation avoids generating that code, but also prevents the wrapper from being restarted or skipped independently.

        See https://slackhq.github.io/compose-lints/rules/#avoid-unnecessary-restart-groups for more information. [ComposeNonRestartableComposable]
        @Composable
        ~~~~~~~~~~~
        0 errors, 0 warnings, 1 hint
        """
          .trimIndent()
      )
  }

  @Test
  fun `suggests non-restartable for a compiled composable call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.CompiledContent
      import androidx.compose.runtime.Composable

      @Composable
      fun Wrapper(content: CompiledContent, value: String) = content.render(value)
      """
        .trimIndent()

    lint()
      .files(compiledStubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:4: Hint: This composable's body only delegates to another composable. Consider marking it @NonRestartableComposable when its own restart and skip boundary is unlikely to be useful. The annotation avoids generating that code, but also prevents the wrapper from being restarted or skipped independently.

        See https://slackhq.github.io/compose-lints/rules/#avoid-unnecessary-restart-groups for more information. [ComposeNonRestartableComposable]
        @Composable
        ~~~~~~~~~~~
        0 errors, 0 warnings, 1 hint
        """
          .trimIndent()
      )
  }

  @Test
  fun `does not suggest non-restartable when the wrapper does other work`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.Content
      import androidx.compose.runtime.State

      class Host
      class Holder(val host: Host)

      @Composable
      fun Host.ReceiverContent(value: String) {}

      @Composable
      fun TwoCalls(value: String) {
        Content(value)
        Content(value)
      }

      @Composable
      fun OtherStatement(value: String) {
        println(value)
        Content(value)
      }

      @Composable
      fun CalculatedArgument(value: String) = Content(value.uppercase())

      @Composable
      fun StateArgument(value: State<String>) = Content(value.value)

      @Composable
      fun InterpolatedArgument(value: String) = Content("Value: ${'$'}value")

      @Composable
      fun NestedContent() = Content("outer") { Content("inner") }

      @Composable
      fun SpreadArgument(values: Array<out String>) =
        androidx.compose.runtime.VarargContent(*values)

      @Composable
      fun PropertyReceiver(holder: Holder, value: String) = holder.host.ReceiverContent(value)

      @Composable
      fun NonComposableCall() = println("not composable")
      """
        .trimIndent()

    lint().files(compiledStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `does not suggest non-restartable for ineligible or explicitly configured wrappers`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.Content
      import androidx.compose.runtime.ExplicitGroupsComposable
      import androidx.compose.runtime.NonRestartableComposable
      import androidx.compose.runtime.NonSkippableComposable
      import androidx.compose.runtime.ReadOnlyComposable
      import androidx.compose.runtime.ReadOnlyContent

      @Composable
      inline fun InlineWrapper(value: String) = Content(value)

      @Composable
      inline fun InlineContent(value: String) = Content(value)

      @Composable
      fun DelegatesToInline(value: String) = InlineContent(value)

      @Composable
      fun ValueWrapper(): String = androidx.compose.runtime.ValueContent()

      @Composable
      @NonRestartableComposable
      fun AlreadyAnnotated(value: String) = Content(value)

      @Composable
      @ExplicitGroupsComposable
      fun ExplicitGroups(value: String) = Content(value)

      @Composable
      @NonSkippableComposable
      fun ExplicitlyNonSkippable(value: String) = Content(value)

      @Composable
      @ReadOnlyComposable
      fun ReadOnlyWrapper() = ReadOnlyContent()

      @Composable
      fun DelegatesToReadOnly() = ReadOnlyContent()

      fun Host() {
        @Composable
        fun LocalWrapper(value: String) = Content(value)
      }

      interface Contract {
        @Composable
        fun InterfaceWrapper(value: String) = Content(value)
      }

      open class Base {
        @Composable
        open fun OpenWrapper(value: String) = Content(value)
      }

      class Implementation : Base() {
        @Composable
        override fun OpenWrapper(value: String) = Content(value)
      }
      """
        .trimIndent()

    lint().files(compiledStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `suggests non-restartable for pass-through chains`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.ExplicitGroupsComposable
      import androidx.compose.runtime.NonRestartableContent
      import androidx.compose.runtime.NonSkippableContent

      @Composable
      @ExplicitGroupsComposable
      fun ExplicitGroupsContent() {}

      @Composable
      fun NonRestartableChild() = NonRestartableContent()

      @Composable
      fun NonSkippableChild() = NonSkippableContent()

      @Composable
      fun ExplicitGroupsChild() = ExplicitGroupsContent()

      @Composable
      fun ExternalChild(value: String) = androidx.compose.runtime.Content(value)
      """
        .trimIndent()

    lint()
      .files(compiledStubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:10: Hint: This composable's body only delegates to another composable. Consider marking it @NonRestartableComposable when its own restart and skip boundary is unlikely to be useful. The annotation avoids generating that code, but also prevents the wrapper from being restarted or skipped independently.

        See https://slackhq.github.io/compose-lints/rules/#avoid-unnecessary-restart-groups for more information. [ComposeNonRestartableComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:13: Hint: This composable's body only delegates to another composable. Consider marking it @NonRestartableComposable when its own restart and skip boundary is unlikely to be useful. The annotation avoids generating that code, but also prevents the wrapper from being restarted or skipped independently.

        See https://slackhq.github.io/compose-lints/rules/#avoid-unnecessary-restart-groups for more information. [ComposeNonRestartableComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:16: Hint: This composable's body only delegates to another composable. Consider marking it @NonRestartableComposable when its own restart and skip boundary is unlikely to be useful. The annotation avoids generating that code, but also prevents the wrapper from being restarted or skipped independently.

        See https://slackhq.github.io/compose-lints/rules/#avoid-unnecessary-restart-groups for more information. [ComposeNonRestartableComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:19: Hint: This composable's body only delegates to another composable. Consider marking it @NonRestartableComposable when its own restart and skip boundary is unlikely to be useful. The annotation avoids generating that code, but also prevents the wrapper from being restarted or skipped independently.

        See https://slackhq.github.io/compose-lints/rules/#avoid-unnecessary-restart-groups for more information. [ComposeNonRestartableComposable]
        @Composable
        ~~~~~~~~~~~
        0 errors, 0 warnings, 4 hints
        """
          .trimIndent()
      )
  }

  @Test
  fun `does not suggest non-restartable when the callee cannot restart`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun IgnoredValueChild() {
        androidx.compose.runtime.ValueContent()
      }
      """
        .trimIndent()

    lint().files(compiledStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `does not suggest non-restartable on previews`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.tooling.preview.Preview

      @Composable
      fun AnotherComposable() {}

      @Preview
      @Composable
      fun IgnoredValueChild() {
        AnotherComposable()
      }
      """
        .trimIndent()

    lint().files(compiledStubs, kotlin(code)).run().expectClean()
  }
}
