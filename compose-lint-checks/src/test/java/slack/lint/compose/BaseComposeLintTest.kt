// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
abstract class BaseComposeLintTest : LintDetectorTest() {

  protected val commonStubs =
    arrayOf(
      kotlin(
        """
          package androidx.compose.ui

          import androidx.compose.runtime.Composable

          @Composable
          interface Modifier {
            infix fun then(other: Modifier): Modifier = other
            companion object : Modifier
          }
      """
          .trimIndent()
      ),
      kotlin(
        """
          package androidx.compose.runtime

          annotation class Composable
          @Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
          annotation class StableMarker
          @StableMarker
          annotation class Stable
          @StableMarker
          annotation class Immutable

          interface State<out T> {
              val value: T
          }

          interface MutableState<T> : State<T> {
              override var value: T
              operator fun component1(): T
              operator fun component2(): (T) -> Unit
          }

          fun <T> mutableStateOf(value: T): MutableState<T> = TODO()

          fun <T> derivedStateOf(
              calculation: () -> T,
          ): State<T> = TODO()

          inline fun <T> remember(crossinline calculation: () -> T): T = TODO()

          fun movableContentOf(content: @Composable () -> Unit): @Composable () -> Unit = TODO()
      """
          .trimIndent()
      ),
      kotlin(
        """
          package androidx.compose.ui.tooling.preview

          @Repeatable
          annotation class Preview

          interface PreviewParameterProvider<T> {
              val values: Sequence<T>
              val count get() = values.count()
          }

          annotation class PreviewParameter(
              val provider: KClass<out PreviewParameterProvider<*>>,
              val limit: Int = Int.MAX_VALUE
          )
      """
          .trimIndent()
      ),
      kotlin(
        """
          package androidx.compose.ui

          import androidx.compose.runtime.Composable
          import androidx.compose.ui.Modifier

          @Composable
          fun Text(text: String, modifier: Modifier = Modifier) {

          }
      """
          .trimIndent()
      ),
    )

  /**
   * Lint periodically adds new "TestModes" to LintDetectorTest. These modes act as a sort of chaos
   * testing mechanism, adding different common variations of code (extra spaces, extra parens, etc)
   * to the test files to ensure that lints are robust against them. They also make it quite
   * difficult to test against and need extra work sometimes to properly support, so we expose this
   * property to allow tests to skip certain modes that need more work in subsequent PRs.
   */
  open val skipTestModes: Array<TestMode>? = null

  abstract override fun getDetector(): Detector

  abstract override fun getIssues(): List<Issue>

  override fun lint(): TestLintTask {
    val lintTask = super.lint()
    lintTask.allowCompilationErrors(false)

    skipTestModes?.let { testModesToSkip -> lintTask.skipTestModes(*testModesToSkip) }
    return lintTask
  }
}
