// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class PreviewPublicDetectorTest : BaseComposeLintTest() {

  private val stubs =
    kotlin(
        """
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.ui.tooling.preview.PreviewParameterProvider

        @Preview
        annotation class CombinedPreviews

        class User
        class UserProvider : PreviewParameterProvider<User>
    """
      )
      .indented()

  override fun getDetector(): Detector = PreviewPublicDetector()

  override fun getIssues(): List<Issue> = listOf(PreviewPublicDetector.ISSUE)

  @Test
  fun `passes for non-preview public composables`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable

        @Composable
        fun MyComposable() { }
      """
        .trimIndent()
    lint().files(stubs, *commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun testDocumentationExample() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        @Composable
        fun MyComposable() { }
        @CombinedPreviews
        @Composable
        fun MyComposable() { }
      """
        .trimIndent()
    lint()
      .files(stubs, *commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:4: Error: Composables annotated with @Preview that are used only for previewing the UI should not be public.See https://slackhq.github.io/compose-lints/rules/#preview-composables-should-not-be-public for more information. [ComposePreviewPublic]
          @Preview
          ^
          src/test.kt:7: Error: Composables annotated with @Preview that are used only for previewing the UI should not be public.See https://slackhq.github.io/compose-lints/rules/#preview-composables-should-not-be-public for more information. [ComposePreviewPublic]
          @CombinedPreviews
          ^
          2 errors, 0 warnings
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
          Autofix for src/test.kt line 4: Make 'private':
          @@ -6 +6
          - fun MyComposable() { }
          + private fun MyComposable() { }
          Autofix for src/test.kt line 7: Make 'private':
          @@ -9 +9
          - fun MyComposable() { }
          + private fun MyComposable() { }
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors when a public preview composable uses preview params`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.ui.tooling.preview.PreviewParameter

        @Preview
        @Composable
        fun MyComposable(@PreviewParameter(User::class) user: User) {
        }
        @CombinedPreviews
        @Composable
        fun MyComposable(@PreviewParameter(User::class) user: User) {
        }
      """
        .trimIndent()
    lint()
      .files(stubs, *commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:5: Error: Composables annotated with @Preview that are used only for previewing the UI should not be public.See https://slackhq.github.io/compose-lints/rules/#preview-composables-should-not-be-public for more information. [ComposePreviewPublic]
          @Preview
          ^
          src/test.kt:9: Error: Composables annotated with @Preview that are used only for previewing the UI should not be public.See https://slackhq.github.io/compose-lints/rules/#preview-composables-should-not-be-public for more information. [ComposePreviewPublic]
          @CombinedPreviews
          ^
          2 errors, 0 warnings
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
          Autofix for src/test.kt line 5: Make 'private':
          @@ -7 +7
          - fun MyComposable(@PreviewParameter(User::class) user: User) {
          + private fun MyComposable(@PreviewParameter(User::class) user: User) {
          Autofix for src/test.kt line 9: Make 'private':
          @@ -11 +11
          - fun MyComposable(@PreviewParameter(User::class) user: User) {
          + private fun MyComposable(@PreviewParameter(User::class) user: User) {
        """
          .trimIndent()
      )
  }

  @Test
  fun `passes when a non-public preview composable uses preview params`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.ui.tooling.preview.PreviewParameter

        @Preview
        @Composable
        private fun MyComposable(@PreviewParameter(User::class) user: User) {
        }
        @CombinedPreviews
        @Composable
        internal fun MyComposable(@PreviewParameter(User::class) user: User) {
        }
      """
        .trimIndent()
    lint().files(stubs, *commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `passes when a private preview composable uses preview params`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.ui.tooling.preview.PreviewParameter

        @Preview
        @Composable
        private fun MyComposable(@PreviewParameter(User::class) user: User) {}

        @CombinedPreviews
        @Composable
        private fun MyComposable(@PreviewParameter(User::class) user: User) {}

      """
        .trimIndent()
    lint().files(stubs, *commonStubs, kotlin(code)).run().expectClean()
  }

  // https://github.com/slackhq/compose-lints/issues/379
  @Test
  fun `test-only public previews are ok`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.annotation.VisibleForTesting

        @VisibleForTesting
        @Preview
        @Composable
        fun MyComposable() {}
      """
        .trimIndent()
    lint()
      .files(
        stubs,
        kotlin(
          """
          package androidx.annotation

          annotation class VisibleForTesting
        """
        ),
        *commonStubs,
        kotlin(code),
      )
      .run()
      .expectClean()
  }
}
