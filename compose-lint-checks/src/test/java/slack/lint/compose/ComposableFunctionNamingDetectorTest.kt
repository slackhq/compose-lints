// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class ComposableFunctionNamingDetectorTest : BaseSlackLintTest() {

  override fun getDetector(): Detector = ComposableFunctionNamingDetector()

  override fun getIssues(): List<Issue> = ComposableFunctionNamingDetector.ISSUES.toList()

  // This mode is irrelevant to our test and totally untestable with stringy outputs
  override val skipTestModes: Array<TestMode> = arrayOf(TestMode.SUPPRESSIBLE, TestMode.TYPE_ALIAS)

  override fun lint(): TestLintTask {
    return super.lint()
      .configureOption(
        ComposableFunctionNamingDetector.ALLOWED_COMPOSABLE_FUNCTION_NAMES,
        ".*Presenter"
      )
  }

  @Test
  fun `passes when a composable that returns values is lowercase`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun myComposable(): Something { }
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `passes when a composable that returns values is uppercase but allowed`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun ProfilePresenter(): Something { }
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `passes when a composable that returns nothing or Unit is uppercase`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun MyComposable() { }
        @Composable
        fun MyComposable(): Unit { }
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `passes when a composable that returns nothing or Unit is lowercase but allowed`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun myPresenter() { }
        @Composable
        fun myPresenter(): Unit { }
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `passes when a composable doesn't have a body block, is a property or a lambda`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun MyComposable() = Text("bleh")

        val composable: Something
            @Composable get() { }

        val composable: Something
            @Composable get() = OtherComposable()

        val whatever = @Composable { }
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `errors when a composable returns a value and is capitalized`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun MyComposable(): Something { }
      """
        .trimIndent()
    lint()
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/test.kt:2: Error: Composable functions that return a value should start with a lowercase letter.
          While useful and accepted outside of @Composable functions, this factory function convention has drawbacks that set inappropriate expectations for callers when used with @Composable functions.
          See https://slackhq.github.io/compose-lints/rules/#naming-composable-functions-properly for more information. [ComposeNamingLowercase]
          fun MyComposable(): Something { }
              ~~~~~~~~~~~~
          1 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors when a composable returns nothing or Unit and is lowercase`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun myComposable() { }

        @Composable
        fun myComposable(): Unit { }
      """
        .trimIndent()

    lint()
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/test.kt:2: Error: Composable functions that return Unit should start with an uppercase letter.
          They are considered declarative entities that can be either present or absent in a composition and therefore follow the naming rules for classes.
          See https://slackhq.github.io/compose-lints/rules/#naming-composable-functions-properly for more information. [ComposeNamingUppercase]
          fun myComposable() { }
              ~~~~~~~~~~~~
          src/test.kt:5: Error: Composable functions that return Unit should start with an uppercase letter.
          They are considered declarative entities that can be either present or absent in a composition and therefore follow the naming rules for classes.
          See https://slackhq.github.io/compose-lints/rules/#naming-composable-functions-properly for more information. [ComposeNamingUppercase]
          fun myComposable(): Unit { }
              ~~~~~~~~~~~~
          2 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `passes when a composable returns nothing or Unit and is lowercase but has a receiver`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun Potato.myComposable() { }

        @Composable
        fun Banana.myComposable(): Unit { }
      """
        .trimIndent()

    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }
}
