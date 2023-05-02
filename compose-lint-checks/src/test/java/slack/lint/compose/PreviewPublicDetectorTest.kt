// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class PreviewPublicDetectorTest : BaseSlackLintTest() {

  override fun getDetector(): Detector = PreviewPublicDetector()

  override fun getIssues(): List<Issue> = listOf(PreviewPublicDetector.ISSUE)

  // This mode is irrelevant to our test and totally untestable with stringy outputs
  override val skipTestModes: Array<TestMode> = arrayOf(TestMode.SUPPRESSIBLE, TestMode.TYPE_ALIAS)

  @Test
  fun `passes for non-preview public composables`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun MyComposable() { }
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `passes for preview public composables that don't have preview params`() {
    @Language("kotlin")
    val code =
      """
        @Preview
        @Composable
        fun MyComposable() { }
        @CombinedPreviews
        @Composable
        fun MyComposable() { }
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `errors when a public preview composable is used when previewPublicOnlyIfParams is false`() {
    @Language("kotlin")
    val code =
      """
        @Preview
        @Composable
        fun MyComposable() { }
        @CombinedPreviews
        @Composable
        fun MyComposable() { }
      """
        .trimIndent()
    lint()
      .configureOption(PreviewPublicDetector.PREVIEW_PUBLIC_ONLY_IF_PARAMS_OPTION, "false")
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/test.kt:1: Error: Composables annotated with @Preview that are used only for previewing the UI should not be public.
          See https://slackhq.github.io/compose-lints/rules/#preview-composables-should-not-be-public for more information. [ComposePreviewPublic]
          @Preview
          ^
          src/test.kt:4: Error: Composables annotated with @Preview that are used only for previewing the UI should not be public.
          See https://slackhq.github.io/compose-lints/rules/#preview-composables-should-not-be-public for more information. [ComposePreviewPublic]
          @CombinedPreviews
          ^
          2 errors, 0 warnings
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
          Autofix for src/test.kt line 1: Make 'private':
          @@ -3 +3
          - fun MyComposable() { }
          + private fun MyComposable() { }
          Autofix for src/test.kt line 4: Make 'private':
          @@ -6 +6
          - fun MyComposable() { }
          @@ -7 +6
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
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/test.kt:1: Error: Composables annotated with @Preview that are used only for previewing the UI should not be public.
          See https://slackhq.github.io/compose-lints/rules/#preview-composables-should-not-be-public for more information. [ComposePreviewPublic]
          @Preview
          ^
          src/test.kt:5: Error: Composables annotated with @Preview that are used only for previewing the UI should not be public.
          See https://slackhq.github.io/compose-lints/rules/#preview-composables-should-not-be-public for more information. [ComposePreviewPublic]
          @CombinedPreviews
          ^
          2 errors, 0 warnings
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
          Autofix for src/test.kt line 1: Make 'private':
          @@ -3 +3
          - fun MyComposable(@PreviewParameter(User::class) user: User) {
          + private fun MyComposable(@PreviewParameter(User::class) user: User) {
          Autofix for src/test.kt line 5: Make 'private':
          @@ -7 +7
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
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `passes when a private preview composable uses preview params`() {
    @Language("kotlin")
    val code =
      """
        @Preview         @Composable         private fun MyComposable(@PreviewParameter(User::class) user: User) {}

        @CombinedPreviews         @Composable         private fun MyComposable(@PreviewParameter(User::class) user: User) {}

      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }
}
