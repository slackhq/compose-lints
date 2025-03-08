// Copyright (C) 2025 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class LocaleGetDefaultDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = LocaleGetDefaultDetector()

  override fun getIssues(): List<Issue> = listOf(LocaleGetDefaultDetector.ISSUE)

  @Test
  fun `errors when composable uses Locale_getDefault()`() {
    @Language("kotlin")
    val code =
      """
                import java.util.Locale
                import androidx.compose.runtime.Composable

                @Composable
                fun Something() {
                    val locale = Locale.getDefault()
                }
            """
        .trimIndent()
    lint()
      .skipTestModes(TestMode.WHITESPACE, TestMode.IMPORT_ALIAS)
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
                src/test.kt:6: Error: Don't use Locale.getDefault() in a @Composable function.
                Use LocalConfiguration.current.locales instead to properly handle locale changes." [LocaleGetDefaultDetector]
                    val locale = Locale.getDefault()
                                 ~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
          .trimIndent()
      )
  }
}
