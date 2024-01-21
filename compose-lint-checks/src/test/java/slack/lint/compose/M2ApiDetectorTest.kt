// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class M2ApiDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = M2ApiDetector()

  override fun getIssues(): List<Issue> = listOf(M2ApiDetector.ISSUE)

  private val Stubs =
    arrayOf(
      kotlin(
        """
          package androidx.compose.material

          import androidx.compose.runtime.Composable

          @Composable
          fun Text(text: String) {
            // no-op
          }

          @Composable
          fun Surface(content: @Composable () -> Unit) {
            // no-op
          }

          object BottomNavigationDefaults {
              val Elevation = 8.dp
          }

          enum class BottomDrawerValue {
              Closed,
              Open,
              Expanded
          }
        """
          .trimIndent()
      ),
      kotlin(
        """
          package androidx.compose.material.ripple

          import androidx.compose.runtime.Composable

          @Composable
          fun rememberRipple()
        """
          .trimIndent()
      ),
    )

  @Test
  fun smokeTest() {
    lint()
      .configureOption(M2ApiDetector.ALLOW_LIST, "Surface")
      .files(
        *Stubs,
        kotlin(
            """
            import androidx.compose.material.BottomDrawerValue
            import androidx.compose.material.BottomNavigationDefaults
            import androidx.compose.material.Text
            import androidx.compose.material.ripple.rememberRipple
            import androidx.compose.runtime.Composable

            @Composable
            fun Example() {
              Text("Hello, world!")
            }

            @Composable
            fun AllowedExample() {
              Surface {

              }
            }

            @Composable
            fun Composite() {
              Surface {
                val ripple = rememberRipple()
                Text("Hello, world!")
                val elevation = BottomNavigationDefaults.Elevation
                val drawerValue = BottomDrawerValue.Closed
              }
            }
          """
          )
          .indented()
      )
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/test.kt:9: Error: Compose Material 2 (M2) is succeeded by Material 3 (M3). Please use M3 APIs.See https://slackhq.github.io/compose-lints/rules/#use-material-3 for more information. [ComposeM2Api]
            Text("Hello, world!")
            ~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:23: Error: Compose Material 2 (M2) is succeeded by Material 3 (M3). Please use M3 APIs.See https://slackhq.github.io/compose-lints/rules/#use-material-3 for more information. [ComposeM2Api]
              Text("Hello, world!")
              ~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:24: Error: Compose Material 2 (M2) is succeeded by Material 3 (M3). Please use M3 APIs.See https://slackhq.github.io/compose-lints/rules/#use-material-3 for more information. [ComposeM2Api]
              val elevation = BottomNavigationDefaults.Elevation
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:25: Error: Compose Material 2 (M2) is succeeded by Material 3 (M3). Please use M3 APIs.See https://slackhq.github.io/compose-lints/rules/#use-material-3 for more information. [ComposeM2Api]
              val drawerValue = BottomDrawerValue.Closed
                                ~~~~~~~~~~~~~~~~~~~~~~~~
          4 errors, 0 warnings
        """
          .trimIndent()
      )
      .expect(
        testMode = TestMode.FULLY_QUALIFIED,
        expectedText =
          """
          src/test.kt:9: Error: Compose Material 2 (M2) is succeeded by Material 3 (M3). Please use M3 APIs.See https://slackhq.github.io/compose-lints/rules/#use-material-3 for more information. [ComposeM2Api]
            Text("Hello, world!")
            ~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:23: Error: Compose Material 2 (M2) is succeeded by Material 3 (M3). Please use M3 APIs.See https://slackhq.github.io/compose-lints/rules/#use-material-3 for more information. [ComposeM2Api]
              Text("Hello, world!")
              ~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:24: Error: Compose Material 2 (M2) is succeeded by Material 3 (M3). Please use M3 APIs.See https://slackhq.github.io/compose-lints/rules/#use-material-3 for more information. [ComposeM2Api]
              val elevation = androidx.compose.material.BottomNavigationDefaults.Elevation
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:25: Error: Compose Material 2 (M2) is succeeded by Material 3 (M3). Please use M3 APIs.See https://slackhq.github.io/compose-lints/rules/#use-material-3 for more information. [ComposeM2Api]
              val drawerValue = androidx.compose.material.BottomDrawerValue.Closed
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          4 errors, 0 warnings
        """
            .trimIndent()
      )
  }
}
