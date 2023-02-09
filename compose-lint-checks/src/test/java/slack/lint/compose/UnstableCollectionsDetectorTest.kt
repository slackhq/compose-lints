// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class UnstableCollectionsDetectorTest : BaseSlackLintTest() {

  override fun getDetector(): Detector = UnstableCollectionsDetector()
  override fun getIssues(): List<Issue> = listOf(UnstableCollectionsDetector.ISSUE)

  // This mode is irrelevant to our test and totally untestable with stringy outputs
  override val skipTestModes: Array<TestMode> =
    arrayOf(TestMode.PARENTHESIZED, TestMode.SUPPRESSIBLE, TestMode.TYPE_ALIAS, TestMode.WHITESPACE)

  @Test
  fun `warnings when a Composable has a Collection List Set Map parameter`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun Something(a: Collection<String>) {}
        @Composable
        fun Something(a: List<String>) {}
        @Composable
        fun Something(a: Set<String>) {}
        @Composable
        fun Something(a: Map<String, Int>) {}
      """
        .trimIndent()

    lint()
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/test.kt:2: Warning: The Compose Compiler cannot infer the stability of a parameter if a Collection<String> is used in it, even if the item type is stable.
          You should use Kotlinx Immutable Collections instead: a: ImmutableCollection<String> or create an @Immutable wrapper for this class: @Immutable data class ACollection(val items: Collection<String>)
          See https://slackhq.github.io/compose-lints/rules/#avoid-using-unstable-collections for more information. [ComposeUnstableCollections]
          fun Something(a: Collection<String>) {}
                           ~~~~~~~~~~~~~~~~~~
          src/test.kt:4: Warning: The Compose Compiler cannot infer the stability of a parameter if a List<String> is used in it, even if the item type is stable.
          You should use Kotlinx Immutable Collections instead: a: ImmutableList<String> or create an @Immutable wrapper for this class: @Immutable data class AList(val items: List<String>)
          See https://slackhq.github.io/compose-lints/rules/#avoid-using-unstable-collections for more information. [ComposeUnstableCollections]
          fun Something(a: List<String>) {}
                           ~~~~~~~~~~~~
          src/test.kt:6: Warning: The Compose Compiler cannot infer the stability of a parameter if a Set<String> is used in it, even if the item type is stable.
          You should use Kotlinx Immutable Collections instead: a: ImmutableSet<String> or create an @Immutable wrapper for this class: @Immutable data class ASet(val items: Set<String>)
          See https://slackhq.github.io/compose-lints/rules/#avoid-using-unstable-collections for more information. [ComposeUnstableCollections]
          fun Something(a: Set<String>) {}
                           ~~~~~~~~~~~
          src/test.kt:8: Warning: The Compose Compiler cannot infer the stability of a parameter if a Map<String, Int> is used in it, even if the item type is stable.
          You should use Kotlinx Immutable Collections instead: a: ImmutableMap<String, Int> or create an @Immutable wrapper for this class: @Immutable data class AMap(val items: Map<String, Int>)
          See https://slackhq.github.io/compose-lints/rules/#avoid-using-unstable-collections for more information. [ComposeUnstableCollections]
          fun Something(a: Map<String, Int>) {}
                           ~~~~~~~~~~~~~~~~
          0 errors, 4 warnings
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
        fun Something(a: ImmutableList<String>, b: ImmutableSet<String>, c: ImmutableMap<String, Int>) {}
        @Composable
        fun Something(a: StringList, b: StringSet, c: StringToIntMap) {}
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }
}
