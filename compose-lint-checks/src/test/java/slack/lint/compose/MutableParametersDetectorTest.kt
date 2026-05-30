// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class MutableParametersDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = MutableParametersDetector()

  override fun getIssues(): List<Issue> = listOf(MutableParametersDetector.ISSUE)

  // Can't get typealias working correctly in this case as the combination of an
  // alias + lint's inability to reach kotlin intrinsic collections defeats it
  override val skipTestModes: Array<TestMode> = arrayOf(TestMode.TYPE_ALIAS)

  @Test
  fun `mutable parameters are reported by default`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.MutableState
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(a: MutableState<String>) {}
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:5: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app. Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.

        See https://slackhq.github.io/compose-lints/rules/#do-not-use-inherently-mutable-types-as-parameters for more information. [ComposeMutableParameters]
        fun Something(a: MutableState<String>) {}
                         ~~~~~~~~~~~~~~~~~~~~
        1 error
        """
          .trimIndent()
      )
  }

  @Test
  fun `mutable collections are reported by default`() {
    @Language("kotlin")
    val code =
      """
      import androidx.collection.MutableScatterMap
      import androidx.collection.MutableScatterSet
      import androidx.compose.runtime.Composable
      import java.util.concurrent.ConcurrentHashMap
      import java.util.LinkedHashMap
      import java.util.TreeMap
      import java.util.TreeSet

      @Composable
      fun Something(a: ArrayList<String>) {}
      @Composable
      fun Something(a: LinkedHashSet<String>) {}
      @Composable
      fun Something(a: MutableMap<String, String>) {}
      @Composable
      fun Something(a: LinkedHashMap<String, String>) {}
      @Composable
      fun Something(a: TreeMap<String, String>) {}
      @Composable
      fun Something(a: TreeSet<String>) {}
      @Composable
      fun Something(a: ConcurrentHashMap<String, String>) {}
      @Composable
      fun Something(a: MutableScatterMap<String, String>) {}
      @Composable
      fun Something(a: MutableScatterSet<String>) {}
      """
        .trimIndent()
    lint()
      .files(
        *commonStubs,
        kotlin(
            """
          package androidx.collection

          class MutableScatterMap<K, V>
          class MutableScatterSet<T>
          """
          )
          .indented(),
        kotlin(code),
      )
      .run()
      .expectErrorCount(9)
  }

  @Test
  fun `errors when a Composable has a mutable parameter`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.MutableState
      import androidx.compose.runtime.Composable

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
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:5: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app. Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.

        See https://slackhq.github.io/compose-lints/rules/#do-not-use-inherently-mutable-types-as-parameters for more information. [ComposeMutableParameters]
        fun Something(a: MutableState<String>) {}
                         ~~~~~~~~~~~~~~~~~~~~
        src/test.kt:7: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app. Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.

        See https://slackhq.github.io/compose-lints/rules/#do-not-use-inherently-mutable-types-as-parameters for more information. [ComposeMutableParameters]
        fun Something(a: ArrayList<String>) {}
                         ~~~~~~~~~~~~~~~~~
        src/test.kt:9: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app. Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.

        See https://slackhq.github.io/compose-lints/rules/#do-not-use-inherently-mutable-types-as-parameters for more information. [ComposeMutableParameters]
        fun Something(a: HashSet<String>) {}
                         ~~~~~~~~~~~~~~~
        src/test.kt:11: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app. Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.

        See https://slackhq.github.io/compose-lints/rules/#do-not-use-inherently-mutable-types-as-parameters for more information. [ComposeMutableParameters]
        fun Something(a: MutableMap<String, String>) {}
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~
        4 errors
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
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `errors when known mutable types are stability annotated`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.Stable
      import androidx.compose.runtime.StableMarker

      @StableMarker
      annotation class MyStableMarker

      @Stable
      class MutableState<T>

      @MyStableMarker
      class MutableSharedFlow<T>

      @Composable
      fun Something(a: MutableState<String>) {}
      @Composable
      fun Something(a: MutableSharedFlow<String>) {}
      """
        .trimIndent()
    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
        src/MyStableMarker.kt:15: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app. Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.

        See https://slackhq.github.io/compose-lints/rules/#do-not-use-inherently-mutable-types-as-parameters for more information. [ComposeMutableParameters]
        fun Something(a: MutableState<String>) {}
                         ~~~~~~~~~~~~~~~~~~~~
        src/MyStableMarker.kt:17: Error: Using mutable objects as state in Compose will cause your users to see incorrect or stale data in your app. Mutable objects that are not observable, such as ArrayList<T> or a mutable data class, cannot be observed by Compose to trigger recomposition when they change.

        See https://slackhq.github.io/compose-lints/rules/#do-not-use-inherently-mutable-types-as-parameters for more information. [ComposeMutableParameters]
        fun Something(a: MutableSharedFlow<String>) {}
                         ~~~~~~~~~~~~~~~~~~~~~~~~~
        2 errors
        """
          .trimIndent()
      )
  }
}
