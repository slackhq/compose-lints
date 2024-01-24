// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class ModifierComposedDetectorTest : BaseComposeLintTest() {

  private val modifierStub =
    kotlin(
      """
        package androidx.compose.ui

        class InspectorInfo {
          companion object {
            val NoInspectorInfo = InspectorInfo()
          }
        }

        interface Modifier {
          companion object : Modifier
        }
        """
        .trimIndent()
    )
  private val composed =
    kotlin(
      "test/androidx/compose/ui/ComposedModifier.kt",
      """
        package androidx.compose.ui

        fun Modifier.composed(
            inspectorInfo: InspectorInfo.() -> Unit = NoInspectorInfo,
            factory: Modifier.() -> Modifier
        ): Modifier {
          TODO()
        }
        """
        .trimIndent(),
    )

  override fun getDetector(): Detector = ModifierComposedDetector()

  override fun getIssues(): List<Issue> = listOf(ModifierComposedDetector.ISSUE)

  @Test
  fun `errors when a composable Modifier extension is detected`() {
    @Language("kotlin")
    val code =
      """
          package test

        import androidx.compose.ui.composed
        import androidx.compose.ui.Modifier

        fun Modifier.something1() = Modifier.composed { }
        fun Modifier.something2() = composed { }
        fun Modifier.something3() = somethingElse()
      """
        .trimIndent()

    lint()
      .files(modifierStub, composed, kotlin(code))
      .run()
      .expect(
        """
          src/test/test.kt:6: Error: Modifier.composed { ... } is no longer recommended due to performance issues.

          You should use the Modifier.Node API instead, as it was designed from the ground up to be far more performant than composed modifiers.

          See https://slackhq.github.io/compose-lints/rules/#migrate-to-modifiernode for more information. [ComposeModifierComposed]
          fun Modifier.something1() = Modifier.composed { }
                                      ~~~~~~~~~~~~~~~~~~~~~
          src/test/test.kt:7: Error: Modifier.composed { ... } is no longer recommended due to performance issues.

          You should use the Modifier.Node API instead, as it was designed from the ground up to be far more performant than composed modifiers.

          See https://slackhq.github.io/compose-lints/rules/#migrate-to-modifiernode for more information. [ComposeModifierComposed]
          fun Modifier.something2() = composed { }
                                      ~~~~~~~~~~~~
          2 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `do not error on a regular composable`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun TextHolder(text: String) {}
      """
        .trimIndent()

    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }
}
