// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test
import kotlin.math.exp

class MutableParametersDetectorTest : BaseSlackLintTest() {

  private val stateStub = kotlin(
    """
      package androidx.compose.runtime

      interface State<out T> {
          val value: T
      }
      
      interface MutableState<T> : State<T> {
          override var value: T
          operator fun component1(): T
          operator fun component2(): (T) -> Unit
      }
    """
  )

  override fun getDetector(): Detector = MutableParametersDetector()

  override fun getIssues(): List<Issue> = listOf(MutableParametersDetector.ISSUE)

  // Can't get typealias working correctly in this case as the combination of an
  // alias + lint's inability to reach kotlin intrinsice collections defeats it
  override val skipTestModes: Array<TestMode> =
    arrayOf(
      TestMode.TYPE_ALIAS
    )

  @Test
  fun `errors when a Composable has a mutable parameter`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.MutableState        

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
      .files(stateStub, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:4: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app.Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.See https://slackhq.github.io/compose-lints/rules/#do-not-use-inherently-mutable-types-as-parameters for more information. [ComposeMutableParameters]
          fun Something(a: MutableState<String>) {}
                           ~~~~~~~~~~~~~~~~~~~~
          src/test.kt:6: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app.Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.See https://slackhq.github.io/compose-lints/rules/#do-not-use-inherently-mutable-types-as-parameters for more information. [ComposeMutableParameters]
          fun Something(a: ArrayList<String>) {}
                           ~~~~~~~~~~~~~~~~~
          src/test.kt:8: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app.Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.See https://slackhq.github.io/compose-lints/rules/#do-not-use-inherently-mutable-types-as-parameters for more information. [ComposeMutableParameters]
          fun Something(a: HashSet<String>) {}
                           ~~~~~~~~~~~~~~~
          src/test.kt:10: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app.Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.See https://slackhq.github.io/compose-lints/rules/#do-not-use-inherently-mutable-types-as-parameters for more information. [ComposeMutableParameters]
          fun Something(a: MutableMap<String, String>) {}
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~
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
        import androidx.compose.runtime.State

        @Composable
        fun Something(a: String, b: (Int) -> Unit) {}
        @Composable
        fun Something(a: State<String>) {}
      """
        .trimIndent()
    lint().files(stateStub, kotlin(code)).run().expectClean()
  }
}
