// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class PreviewNamingDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = PreviewNamingDetector()

  override fun getIssues(): List<Issue> = listOf(PreviewNamingDetector.ISSUE)

  @Test
  fun `passes for non-preview annotations`() {
    @Language("kotlin")
    val code =
      """
        annotation class Banana
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `passes for preview annotations with the proper names`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.ui.tooling.preview.Preview

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
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `errors when a multipreview annotation is not correctly named for 1 preview`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        annotation class Banana
        @Preview
        annotation class BananaPreviews
        @BananaPreviews
        annotation class WithBananaPreviews
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/Banana.kt:3: Error: Preview annotations with 1 preview annotations should end with the Preview suffix.
          See https://slackhq.github.io/compose-lints/rules/#naming-multipreview-annotations-properly for more information. [ComposePreviewNaming]
          @Preview
          ^
          src/Banana.kt:5: Error: Preview annotations with 1 preview annotations should end with the Preview suffix.
          See https://slackhq.github.io/compose-lints/rules/#naming-multipreview-annotations-properly for more information. [ComposePreviewNaming]
          @Preview
          ^
          src/Banana.kt:7: Error: Preview annotations with 1 preview annotations should end with the Preview suffix.
          See https://slackhq.github.io/compose-lints/rules/#naming-multipreview-annotations-properly for more information. [ComposePreviewNaming]
          @BananaPreviews
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
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        @Preview
        @Repeatable
        annotation class BananaPreview
        @BananaPreview
        @BananaPreview
        annotation class BananaPreview
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .allowDuplicates()
      .run()
      .expect(
        """
          src/BananaPreview.kt:3: Error: Preview annotations with 2 preview annotations should end with the Previews suffix.
          See https://slackhq.github.io/compose-lints/rules/#naming-multipreview-annotations-properly for more information. [ComposePreviewNaming]
          @Preview
          ^
          src/BananaPreview.kt:6: Error: Preview annotations with 2 preview annotations should end with the Previews suffix.
          See https://slackhq.github.io/compose-lints/rules/#naming-multipreview-annotations-properly for more information. [ComposePreviewNaming]
          @BananaPreview
          ^
          2 errors, 0 warnings
        """
          .trimIndent()
      )
  }
}
