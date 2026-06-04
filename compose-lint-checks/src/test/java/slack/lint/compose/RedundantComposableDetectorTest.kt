// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestFiles.bytecode
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class RedundantComposableDetectorTest : BaseComposeLintTest() {

  // Self-contained stubs: the callable composables are exempt from the rule (Text is `external` so
  // it has no body; CompositionLocal.current is an interface member), so the stubs produce no
  // warnings.
  private val stubs =
    kotlin(
      """
      package androidx.compose.runtime

      annotation class Composable

      annotation class ReadOnlyComposable

      interface CompositionLocal<T> {
        val current: T
          @Composable get() = error("stub")
      }

      fun <T> compositionLocalOf(defaultFactory: () -> T): CompositionLocal<T> = error("stub")

      interface State<out T> {
        val value: T
      }

      interface MutableState<T> : State<T> {
        override var value: T
      }

      @Composable external fun Text(text: String)
      """
        .trimIndent()
    )

  override fun getDetector(): Detector = RedundantComposableDetector()

  override fun getIssues(): List<Issue> = RedundantComposableDetector.ISSUES.toList()

  @Test
  fun `errors when a composable does not use composition`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun redundant() = println("derp")

      @Composable
      fun stillRedundant(name: String) {
        println(name.length)
      }

      val redundantProperty: Int
        @Composable get() = 3
      """
        .trimIndent()
    lint()
      .files(stubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:3: Warning: This declaration is annotated with @Composable but doesn't call any other @Composable functions or read any @Composable properties (like a CompositionLocal's current), so it doesn't use the composition and the @Composable annotation can be removed.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeRedundantComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:6: Warning: This declaration is annotated with @Composable but doesn't call any other @Composable functions or read any @Composable properties (like a CompositionLocal's current), so it doesn't use the composition and the @Composable annotation can be removed.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeRedundantComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:12: Warning: This declaration is annotated with @Composable but doesn't call any other @Composable functions or read any @Composable properties (like a CompositionLocal's current), so it doesn't use the composition and the @Composable annotation can be removed.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeRedundantComposable]
          @Composable get() = 3
          ~~~~~~~~~~~
        0 errors, 3 warnings
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
        Autofix for src/test.kt line 3: Remove redundant @Composable:
        @@ -3 +2,0 @@
        -@Composable
        Autofix for src/test.kt line 6: Remove redundant @Composable:
        @@ -6 +5,0 @@
        -@Composable
        Autofix for src/test.kt line 12: Remove redundant @Composable:
        @@ -12 +12 @@
        -  @Composable get() = 3
        +  get() = 3
        """
          .trimIndent()
      )
  }

  @Test
  fun `informational when only CompositionLocals are read`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.CompositionLocal
      import androidx.compose.runtime.compositionLocalOf

      val LocalThing: CompositionLocal<Int> = compositionLocalOf { 0 }

      @Composable
      fun readsCompositionLocal() {
        println(LocalThing.current)
      }

      val themed: Int
        @Composable get() = LocalThing.current
      """
        .trimIndent()
    lint()
      .files(stubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:7: Hint: This declaration only uses the composition to read CompositionLocal values, so it can be annotated with @ReadOnlyComposable to avoid generating a group around its body.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeReadOnlyComposable]
        @Composable
        ~~~~~~~~~~~
        src/test.kt:13: Hint: This declaration only uses the composition to read CompositionLocal values, so it can be annotated with @ReadOnlyComposable to avoid generating a group around its body.

        See https://slackhq.github.io/compose-lints/rules/#remove-unnecessary-composable-annotations for more information. [ComposeReadOnlyComposable]
          @Composable get() = LocalThing.current
          ~~~~~~~~~~~
        0 errors, 0 warnings, 2 hints
        """
          .trimIndent()
      )
      .expectFixDiffs(
        """
        Autofix for src/test.kt line 7: Annotate with @ReadOnlyComposable:
        @@ -2,0 +3 @@
        +import androidx.compose.runtime.ReadOnlyComposable
        @@ -7,0 +9 @@
        +@ReadOnlyComposable
        Autofix for src/test.kt line 13: Annotate with @ReadOnlyComposable:
        @@ -2,0 +3 @@
        +import androidx.compose.runtime.ReadOnlyComposable
        @@ -13 +14,2 @@
        -  @Composable get() = LocalThing.current
        +  @Composable
        +  @ReadOnlyComposable get() = LocalThing.current
        """
          .trimIndent()
      )
  }

  @Test
  fun `no errors when composition is used`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.CompositionLocal
      import androidx.compose.runtime.compositionLocalOf
      import androidx.compose.runtime.Text

      val LocalThing: CompositionLocal<Int> = compositionLocalOf { 0 }

      @Composable
      fun callsComposable() {
        Text("hi")
      }

      @Composable
      fun readsCompositionLocalAndCallsComposable() {
        println(LocalThing.current)
        Text("hi")
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors when already annotated with ReadOnlyComposable`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.CompositionLocal
      import androidx.compose.runtime.ReadOnlyComposable
      import androidx.compose.runtime.compositionLocalOf

      val LocalThing: CompositionLocal<Int> = compositionLocalOf { 0 }

      @Composable
      @ReadOnlyComposable
      fun readsCompositionLocal() {
        println(LocalThing.current)
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors when composition is only used in a default argument value`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.CompositionLocal
      import androidx.compose.runtime.ReadOnlyComposable
      import androidx.compose.runtime.compositionLocalOf

      val LocalThing: CompositionLocal<Int> = compositionLocalOf { 0 }

      // A @Composable function, used below as a default argument value.
      @Composable
      @ReadOnlyComposable
      fun provideValue(): Int = LocalThing.current

      // The bodies use no composition, but each default value invokes the composition (a @Composable
      // function call / a @Composable property read), so the @Composable annotation is required:
      // removing it would be a compile error. The rule only inspects the body and so must not flag these.
      @Composable
      fun usesComposableFunctionDefault(value: Int = provideValue()): Int = value

      @Composable
      fun usesComposablePropertyDefault(value: Int = LocalThing.current): Int = value
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors when reading or writing State value`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.State
      import androidx.compose.runtime.MutableState

      @Composable
      fun readsState(state: State<Int>): Int {
        return state.value
      }

      @Composable
      fun writesState(state: MutableState<Int>) {
        state.value = 5
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors for composables that take a composable slot`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Wrapper(content: @Composable () -> Unit) {
        println("doesn't even call content")
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors for composables that take typealiased composable slot`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      typealias ComposableTypealias = @Composable () -> Unit

      @Composable
      fun Wrapper(content: ComposableTypealias) {
        println("doesn't even call content")
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors for overrides and overridable declarations`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      interface Screen {
        @Composable fun Content()
      }

      class Home : Screen {
        @Composable override fun Content() {
          println("nothing composable here")
        }
      }

      abstract class Base {
        @Composable open fun Render() {
          println("nothing composable here")
        }
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }

  // ---- Binary-dependency false-positive repro --------------------------------
  //
  // When a @Composable function is compiled into a binary (e.g. a library AAR),
  // the lint test infrastructure provides it as a JAR rather than source.  The
  // detector resolves the call via PsiMethod → UMethod and then calls
  // findAnnotation("androidx.compose.runtime.Composable") to decide whether the
  // callee is composable.  If that resolution fails for binary-backed PSI
  // elements the rule incorrectly fires on the caller.
  //
  // Repro observed in practice with Instacart Design System (IDS): functions
  // such as TopNavigationDefault, ICScaffold.Scaffold, and BaseInputTextField
  // all come from a pre-compiled AAR, and callers in the app were flagged even
  // though their @Composable annotation was required.

  // Binary stub compiled with @Retention(AnnotationRetention.BINARY) on @Composable, which
  // matches the real androidx.compose.runtime.Composable.  The @Composable annotation is
  // therefore stored in RuntimeInvisibleAnnotations (CLASS retention) in the class file.
  // If lint's PSI layer only reads RuntimeVisibleAnnotations (RUNTIME retention) from binaries,
  // it will fail to find @Composable on binary methods and fire a false positive.
  //
  // LibraryComposableWithValueClass takes a @JvmInline value class parameter, causing the
  // Kotlin compiler to mangle the JVM method name to LibraryComposableWithValueClass-Yj-AmPM.
  // If lint's resolve() cannot match the Kotlin-level call to the mangled JVM method, it returns
  // null and isComposable() returns false — another source of false positives.
  @Suppress("DEPRECATION")
  private val binaryComposableLibrary =
    bytecode(
      "libs/library.jar",
      kotlin(
          """
          package com.example.library

          import androidx.compose.runtime.Composable

          @JvmInline value class LibraryColor(val value: Long)

          @Composable
          fun LibraryComposable() {}

          @Composable
          fun LibraryComposableWithValueClass(color: LibraryColor = LibraryColor(0L)) {}

          class LibraryComponent {
            @Composable
            fun Render() {}
          }
          """
        )
        .indented(),
      // Class files compiled with @Composable having AnnotationRetention.BINARY (matching
      // real androidx.compose.runtime.Composable). The annotation is stored in
      // RuntimeInvisibleAnnotations in the class file — lint's PSI layer may not read these
      // from binary PSI elements, causing false-positive ComposeRedundantComposable reports.
      """
      com/example/library/LibraryKt.class:
      H4sIAAAAAAAC/41RTW/TQBB9a+fDcVvqlo826SelpSkQ3FbcKgRVJNSEFBBF
      QainjWNgG9sbeZ2qXFD/Blf+AbeKA6o48qMQs44rCpUolnb2zezM88ybHz+/
      fgPwAPcY5jwZuv4RD/uB7waiE/P4g9sa3k+TIhiDc8APuRvw6J37vHPgexQ1
      GSaypLoM+1LxTuAzmNW1NsNKi0fdWIrukeulj74bD6JEhL77O3mLYfUCw2uR
      vG/zYODXA65U7c1BbTt8scuQqzY1cd6TgYwZWJPB/c/i5a7/lg+ChGGq2my0
      /h5lS/Mu/kODuv5lEVdIBk9GKokHXiLjmqDcYV/NUTiYsDGOyVFYKNkwcE3L
      05NJICJ31094lyecBjbCQ5N0N7QxtQHN0qP4kdDeOqHuBsPD0+NJ+/TYNhzD
      Niw60+ldsZzT44qxznaKlbJjaLRpWYZjEsrtFL9/LuSsvFPQJJtMUy9cIhHD
      UuuyyalrO/Pv90jEXF12adHjLRH5zwZhx49fDVc/83K44kZ0KJSg0HYUyYQn
      gkRjmGxJjwdtHgudnZWM7SXc6+3yfubbe3IQe/4ToZ1yxte+wIYNUjin1YOJ
      MvIokL9CXhnDj31Jlb1NtkA3aCurGS6mabSlrOSuXsdZiXGuZARVsqMpps7I
      x3kSp0RvY4Q1yVk/zmzu4ydYpRNcfTR/gutDzrW0TzbyB7mBO+njctrBY4re
      oEGm9mE2MN1AuYEKZghitoE5zO+DKSxgcR9FhbzCTYUlBVvhloKlUPoFauQN
      vdEDAAA=
      """,
      """
      com/example/library/LibraryComponent.class:
      H4sIAAAAAAAC/41Qy27TQBQ9M06c4KbELa+05SlRxENi0oodDwkqIYwMSAVl
      k9XEHpVp7JnKdqKwy7fwB6yQWKCIJR+FuON4gWCDJZ975tx75s69P399+w7g
      EW4x3E5sLtRC5meZEpmeFLL4JOJ1PLL5mTXKVB0whvBUzqXIpDkR7yanKiHV
      Y/CfaKOrZwze3XujHtrwA7TQYWhVH3XJcCf+nwaP6aJjZVJVMOzH0qSF1elC
      JC5fKlHMTKVzJer6Uk4yRYateGqrTBvxRlUylZUkjedzjybjDjwHYGBTR2gA
      vtCODYmlBwxitQwDPuABD1fLgHc9InywWh7yIXvR+fHZb3V56L3uhq1dPmy/
      6jjbIXM3Bs3zH04rmvPIpoqhH2uj3s7yiSo+uAcybMc2kdlIFtqdG3HveD1K
      ZOa61CQ9N8ZWstLW0LKC93ZWJOqldqU7Tenon0IcgNOS3cfpObRzwj06CTcu
      xfb9r+h+qdNXCf1a3MQ1wt66AOcQUNzCBmV5bX5Qb4v+v439P4ysMV5vsr26
      9kaNu7hJ8Smpm9Tg/BhehH6EMKI22xEu4GKES7g8BitxBYMx/BJBiZ0S7dLx
      DeK/AU9ugNubAgAA
      """,
      """
      com/example/library/LibraryColor.class:
      H4sIAAAAAAAC/4VUXVMbVRh+zsluslkW2ISPEqgptLZNKDRAVay0QEFrE0Nb
      oUYpfswSdmAh2cXshsE7HC/0B3ScsZe98YbO2BkFxs44iHf+JsfxPZsFYoh2
      JnPej30/nvO878mff//6G4A38AVDf9EpZ8xto7xZMjMla7liVL7K5Gty1ik5
      lQgYg75ubBmZkmGvZh4sr5tFL4IQg7JqegWjVDUZQql0jkHeqlkspyECJQqO
      KIPkrVkuw8X8q1pNMLR6zoJXsezVYYvCGLpSuXT+tHntG8Wda/TNVK3Siklo
      2xnCtyzb8iZ9VAUNMcRV6Ohg6KjvlvLB3lbQRRnG5qZprzAMp852OwsgaDah
      4Rx6RO0Ew/lmSOsD+0TgeRE4+/+BSRF4geg95oKhM9WEBQ0DuChiLxHHRmV1
      REMr2lQi/QoRuWa4a7POihkQKRG8LEPbaZW8Y69GMEh9jkM1DCGt4hqGfeqy
      GlLC5hhhaDG/rBolN6jWncrlG1diIv2YQa3ay862H6XRgoVF9pu0GI63ZlYY
      4meziPxaaTHsZkU1jOGGqDNRu0VBhSSGqRcd2/Uq1aLnVOouSWuoHENguCym
      8sq9EztyW7SYpd3dYtDqLkt3l1O5nLgc3xwVxxjdP7/heCXLzqxvlTO5rXLW
      JsMkgLHjD3OmZ6wYnkE+Xt4K0XPj4giJA9RlQyj0svi2JTTqwleo+ovDnUGV
      93CV64c7Kv24HlW5IpNsISmRVEiGSLaR5MrRt9M9hztjfITNtMfDOu/lI6Gj
      Z2FJkXQ516crZEfHFF3tlXrYCLv3x/dH30j+9xZdy8X0VvreJrxf17ztuk7e
      GHnjdd4OvXM+dlJbIVy9khLWI0ffMS6AEyN0JzXg9PqGR6MQC8XQnide7lfL
      y2blkbFcMsUKOEWjVDAqlrADZ+uCZxQ35ozNwFYXnGqlaN61hJGYr9qeVTYL
      lmvR1zu27XiGZ9H0MUozk3xC4+IPhzQxShlh8iySlaHvhA3y4M9Qn4sh4DGd
      4ZoTS35CTW8hDYiKJxQkjyNE8UDiJfTFfXTGu/fQm9zDa3p6D/17eP0nv/Np
      kQQu+xiYeJhBkSsBAkUgOMDVxhzlpDG9tSDnEuWIxnLyANd3GxLkkyZDdL2m
      TUZ3/7MJPaUg5yERJ5PsG/od/Cnk0O7QIfge3ppJDjz5QdjSrk/Yp3RGwKN/
      oV3ya3b74PoCHEIbx9s+gpt4J6g+GnAXFYiuHeDWKaRaejSAJDSRznQuHmGQ
      Pknp4lmog/uYHLzwC9QXTYdXq6We1FL9LWBUcwrTQa3+gE2efN5AC6/tjJ7A
      HcwE0VeJFh/fS/DF5D7ebRxYFO/5STHx91Y3sH+tGWuyWgncxftBwhR1EXur
      JfufPEVE+hFS6JRtGVydridLw72Aaw1Z0sSFPvPDP8HnJG3SciQ/oNT8EkJZ
      zGVxP4sHeJjFh5jPYgGPlsBcfITCErpcaC4+dhHxzykX0y5kF2EXN33PuIsx
      FzdcDPlmykXaxYCvt7po+wcGa27RQAgAAA==
      """,
      """
      META-INF/main.kotlin_module:
      H4sIAAAAAAAC/2NgYGBmYGBgYoDQYMClwCWcnJ+rl1qRmFuQk6qXk5lUlFhU
      KcTpA2F4lygxaDEAAD+nAj06AAAA
      """,
    )

  @Test
  fun `no errors when calling a top-level composable from a binary dependency`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import com.example.library.LibraryComposable

      @Composable
      fun Caller() {
        LibraryComposable()
      }
      """
        .trimIndent()
    lint().files(stubs, binaryComposableLibrary, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors when calling a member composable from a binary dependency`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import com.example.library.LibraryComponent

      @Composable
      fun Caller(component: LibraryComponent) {
        component.Render()
      }
      """
        .trimIndent()
    lint().files(stubs, binaryComposableLibrary, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors when calling a binary composable whose JVM name is mangled by a value class parameter`() {
    // LibraryComposableWithValueClass takes a @JvmInline value class (LibraryColor), so the
    // Kotlin compiler mangles its JVM method name to LibraryComposableWithValueClass-Yj-AmPM.
    // If lint's resolve() matches the Kotlin-level call to the mangled JVM method and can still
    // see @Composable on it, no false positive should fire.
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import com.example.library.LibraryColor
      import com.example.library.LibraryComposableWithValueClass

      @Composable
      fun Caller() {
        LibraryComposableWithValueClass(color = LibraryColor(0L))
      }
      """
        .trimIndent()
    lint().files(stubs, binaryComposableLibrary, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors when invoking a composable lambda stored in a property`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      sealed interface State {
        data object A : State
        class B(val composable: @Composable () -> Unit) : State
      }

      @Composable
      fun HandleState(state: State) {
        when (state) {
          State.A -> Unit
          is State.B -> state.composable()
        }
      }
      """
        .trimIndent()
    lint().files(stubs, kotlin(code)).run().expectClean()
  }
}
