// Copyright (C) 2023 Slack Technologies, LLC
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class MutableParametersDetectorTest : BaseSlackLintTest() {

  override fun getDetector(): Detector = MutableParametersDetector()
  override fun getIssues(): List<Issue> = listOf(MutableParametersDetector.ISSUE)

  // This mode is irrelevant to our test and totally untestable with stringy outputs
  override val skipTestModes: Array<TestMode> =
    arrayOf(
      TestMode.PARENTHESIZED,
      TestMode.SUPPRESSIBLE,
      TestMode.TYPE_ALIAS,
      TestMode.FULLY_QUALIFIED,
      TestMode.WHITESPACE
    )

  @Test
  fun `errors when a Composable has a mutable parameter`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun Something(a: MutableState<String>) {}
        @Composable
        fun Something(a: ArrayList<String>) {}
        @Composable
        fun Something(a: HashSet<String>) {}
        @Composable
        fun Something(a: MutableMap<String, String>) {}
      """
        .trimIndent()
    lint()
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/test.kt:2: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app.
          Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.
          See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeMutableParameters]
          fun Something(a: MutableState<String>) {}
              ~~~~~~~~~
          src/test.kt:4: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app.
          Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.
          See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeMutableParameters]
          fun Something(a: ArrayList<String>) {}
              ~~~~~~~~~
          src/test.kt:6: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app.
          Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.
          See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeMutableParameters]
          fun Something(a: HashSet<String>) {}
              ~~~~~~~~~
          src/test.kt:8: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app.
          Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.
          See https://slackhq.github.io/compose-lints/rules/#when-should-i-expose-modifier-parameters for more information. [ComposeMutableParameters]
          fun Something(a: MutableMap<String, String>) {}
              ~~~~~~~~~
          4 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `no errors when a Composable has valid parameters`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun Something(a: String, b: (Int) -> Unit) {}
        @Composable
        fun Something(a: State<String>) {}
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }
}
