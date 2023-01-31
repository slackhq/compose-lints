// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class PreviewNamingDetectorTest : BaseSlackLintTest() {

  override fun getDetector(): Detector = PreviewNamingDetector()
  override fun getIssues(): List<Issue> = listOf(PreviewNamingDetector.ISSUE)

  // This mode is irrelevant to our test and totally untestable with stringy outputs
  override val skipTestModes: Array<TestMode> = arrayOf(TestMode.SUPPRESSIBLE, TestMode.TYPE_ALIAS)

  @Test
  fun `passes for non-preview annotations`() {
    @Language("kotlin")
    val code =
      """
        annotation class Banana
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `passes for preview annotations with the proper names`() {
    @Language("kotlin")
    val code =
      """
        @Preview
        annotation class BananaPreview
        @BananaPreview
        annotation class DoubleBananaPreview
        @Preview
        @Preview
        annotation class ApplePreviews
        @Preview
        @ApplePreviews
        annotation class CombinedApplePreviews
        @BananaPreview
        @ApplePreviews
        annotation class FruitBasketPreviews
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `errors when a multipreview annotation is not correctly named for 1 preview`() {
    @Language("kotlin")
    val code =
      """
        @Preview
        annotation class Banana
        @Preview
        annotation class BananaPreviews
        @BananaPreview
        annotation class WithBananaPreviews
      """
        .trimIndent()
    lint()
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/Banana.kt:1: Error: Preview annotations with 1 preview annotations should end with the Preview suffix.
          See https://twitter.github.io/compose-rules/rules/#naming-multipreview-annotations-properly for more information. [ComposePreviewNaming]
          @Preview
          ^
          src/Banana.kt:3: Error: Preview annotations with 1 preview annotations should end with the Preview suffix.
          See https://twitter.github.io/compose-rules/rules/#naming-multipreview-annotations-properly for more information. [ComposePreviewNaming]
          @Preview
          ^
          src/Banana.kt:5: Error: Preview annotations with 1 preview annotations should end with the Preview suffix.
          See https://twitter.github.io/compose-rules/rules/#naming-multipreview-annotations-properly for more information. [ComposePreviewNaming]
          @BananaPreview
          ^
          3 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors when a multipreview annotation is not correctly named for multi previews`() {
    @Language("kotlin")
    val code =
      """
        @Preview
        @Preview
        annotation class BananaPreview
        @BananaPreview
        @BananaPreview
        annotation class BananaPreview
      """
        .trimIndent()
    lint()
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/BananaPreview.kt:1: Error: Preview annotations with 2 preview annotations should end with the Previews suffix.
          See https://twitter.github.io/compose-rules/rules/#naming-multipreview-annotations-properly for more information. [ComposePreviewNaming]
          @Preview
          ^
          src/BananaPreview.kt:4: Error: Preview annotations with 2 preview annotations should end with the Previews suffix.
          See https://twitter.github.io/compose-rules/rules/#naming-multipreview-annotations-properly for more information. [ComposePreviewNaming]
          @BananaPreview
          ^
          2 errors, 0 warnings
        """
          .trimIndent()
      )
  }
}
