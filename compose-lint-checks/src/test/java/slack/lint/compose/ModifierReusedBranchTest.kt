// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestFiles.binaryStub
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class ModifierReusedBranchTest : BaseComposeLintTest() {

  private fun TestLintResult.expectNotForwardedErrors(count: Int) {
    expectErrorCount(count)
    val diagnostic =
      Regex.escape(
        "Error: One branch passes the modifier parameter, but this branch does not. Pass it to every branch whose composable accepts a modifier."
      ) + ".*?\\[ComposeModifierReused]"
    expectMatches("(?s)(?:.*?$diagnostic){$count}.*")
  }

  private fun TestLintResult.expectReusedModifierErrors(count: Int) {
    expectErrorCount(count)
    val diagnostic =
      Regex.escape(
        "Error: The modifier is passed to more than one composable on this execution path."
      ) + ".*?\\[ComposeModifierReused]"
    expectMatches("(?s)(?:.*?$diagnostic){$count}.*")
  }

  private val compiledBranches =
    binaryStub(
      "libs/compiled-branches.jar",
      kotlin(
          """
          package androidx.compose.runtime

          @Target(AnnotationTarget.FUNCTION)
          @Retention(AnnotationRetention.BINARY)
          annotation class Composable
          """
        )
        .indented()
        .to("src/androidx/compose/runtime/Composable.kt"),
      kotlin(
          """
          package androidx.compose.ui

          interface Modifier {
            companion object : Modifier
          }
          """
        )
        .indented()
        .to("src/androidx/compose/ui/Modifier.kt"),
      kotlin(
          """
          package compiled

          import androidx.compose.runtime.Composable
          import androidx.compose.ui.Modifier

          enum class State { Loading, Loaded }

          class CompiledBranches {
            @Composable
            fun loadingContent(value: String, modifier: Modifier = Modifier) {}

            @Composable
            fun loadedContent(value: String, modifier: Modifier = Modifier) {}
          }
          """
        )
        .indented()
        .to("src/compiled/Content.kt"),
    )

  override fun getDetector(): Detector = ModifierReusedDetector()

  override fun getIssues(): List<Issue> = listOf(ModifierReusedDetector.ISSUE)

  // https://github.com/slackhq/compose-lints/issues/466
  @Test
  fun `reports when a modifier is omitted from one when branch`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      sealed interface SomeViewState {
        data object Loading : SomeViewState
        data object Loaded : SomeViewState
      }

      @Composable
      fun SomeScreenContent(
        state: SomeViewState,
        modifier: Modifier = Modifier,
      ) {
        when (state) {
          is SomeViewState.Loading -> SomeScreenLoadingContent()
          is SomeViewState.Loaded -> SomeScreenLoadedContent(modifier)
        }
      }

      @Composable
      fun SomeScreenLoadingContent(modifier: Modifier = Modifier) = Unit

      @Composable
      fun SomeScreenLoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
        src/SomeViewState.kt:15: Error: One branch passes the modifier parameter, but this branch does not. Pass it to every branch whose composable accepts a modifier.

        See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
            is SomeViewState.Loading -> SomeScreenLoadingContent()
                                        ~~~~~~~~~~~~~~~~~~~~~~~~
        1 error
        """
          .trimIndent()
      )
  }

  @Test
  fun `reports every top-level branch that does not forward the modifier`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      enum class State { First, Second, Third }

      @Composable
      fun Content(state: State, modifier: Modifier = Modifier) =
        when (state) {
          State.First -> FirstContent(modifier = modifier)
          State.Second -> SecondContent(modifier = Modifier)
          State.Third -> ThirdContent()
        }

      @Composable fun FirstContent(modifier: Modifier = Modifier) = Unit
      @Composable fun SecondContent(modifier: Modifier = Modifier) = Unit
      @Composable fun ThirdContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
        src/State.kt:10: Error: One branch passes the modifier parameter, but this branch does not. Pass it to every branch whose composable accepts a modifier.

        See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
            State.Second -> SecondContent(modifier = Modifier)
                            ~~~~~~~~~~~~~
        src/State.kt:11: Error: One branch passes the modifier parameter, but this branch does not. Pass it to every branch whose composable accepts a modifier.

        See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
            State.Third -> ThirdContent()
                           ~~~~~~~~~~~~
        2 errors
        """
          .trimIndent()
      )
  }

  @Test
  fun `reports a missing modifier in a top-level if branch`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) {
          LoadingContent()
        } else {
          LoadedContent(modifier.then(Modifier))
        }
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports a missing modifier from a returned top-level conditional`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier): Unit {
        return if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports after setup declarations and follows modifier aliases`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        val firstAlias = modifier
        val forwardedModifier = firstAlias
        if (loading) LoadingContent() else LoadedContent(forwardedModifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports after a value composable setup that does not consume the modifier`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.remember
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        val label = remember { "content" }
        if (loading) LoadingContent(label) else LoadedContent(label, modifier)
      }

      @Composable fun LoadingContent(label: String, modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(label: String, modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports after a require call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, valid: Boolean, modifier: Modifier = Modifier) {
        require(valid)
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports after an early guard return`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
        if (!enabled) return
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports after setup declarations inside a branch`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) {
          val label = "loading"
          LoadingContent(label)
        } else {
          LoadedContent("loaded", modifier)
        }
      }

      @Composable
      fun LoadingContent(label: String, modifier: Modifier = Modifier) = Unit

      @Composable
      fun LoadedContent(label: String, modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports after a println call inside a branch`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) {
          println("loading")
          LoadingContent()
        } else {
          LoadedContent(modifier)
        }
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `does not check a conditional after content was already emitted`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        val ignored: Any? = Header(modifier)
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun Header(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectReusedModifierErrors(2)
  }

  @Test
  fun `does not check a conditional after a nested content call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        val ignored = listOf(Header(modifier))
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun Header(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectReusedModifierErrors(2)
  }

  @Test
  fun `does not report missing forwarding after a slot emits content`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable (Modifier) -> Unit,
      ) {
        content(modifier)
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectReusedModifierErrors(2)
  }

  @Test
  fun `does not report missing forwarding after a value composable consumes the modifier`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        val ignored = Header(modifier)
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun Header(modifier: Modifier = Modifier): Int = 1
      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectReusedModifierErrors(2)
  }

  @Test
  fun `does not check a conditional after content emitted by run`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        val ignored = run { Header(modifier) }
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun Header(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectReusedModifierErrors(2)
  }

  @Test
  fun `reports after a stored lambda captures the modifier`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable (Modifier) -> Unit,
      ) {
        val deferred: @Composable () -> Unit = { content(modifier) }
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports missing forwarding after a constructor captures the modifier`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      class Holder(val content: @Composable () -> Unit)

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        val deferred = Holder { Header(modifier) }
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun Header(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports a missing modifier after repeat with count 0`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        repeat(0) { Header(modifier) }
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun Header(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `does not check a conditional after a composable call with a lambda`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        val ignored = Execute { Header() }
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable
      fun Execute(content: @Composable () -> Unit): Int {
        content()
        return 0
      }

      @Composable fun Header() = Unit
      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `does not check a conditional after a Unit composable call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        SideEffect {}
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun SideEffect(effect: () -> Unit) = effect()
      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `reports a missing modifier in a nested conditional branch`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      enum class State { First, Other }

      @Composable
      fun Content(state: State, loading: Boolean, modifier: Modifier = Modifier) {
        when (state) {
          State.First -> FirstContent(modifier)
          State.Other -> if (loading) LoadingContent() else LoadedContent(modifier = modifier)
        }
      }

      @Composable fun FirstContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `allows forwarding to every applicable top-level branch`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      enum class State { First, Second, Third }

      @Composable
      fun Content(state: State, modifier: Modifier = Modifier) {
        when (state) {
          State.First -> FirstContent(modifier)
          State.Second -> SecondContent(modifier = modifier)
          State.Third -> ThirdContent(modifier.then(Modifier))
        }
      }

      @Composable fun FirstContent(modifier: Modifier = Modifier) = Unit
      @Composable fun SecondContent(modifier: Modifier = Modifier) = Unit
      @Composable fun ThirdContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `allows forwarding the modifier through run results`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) LoadingContent(modifier) else LoadedContent(run { modifier })
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `allows forwarding a modifier alias produced by run`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        val adjusted = run { modifier }
        if (loading) LoadingContent(modifier) else LoadedContent(adjusted)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `reports when run produces a fresh modifier`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) LoadingContent(modifier) else LoadedContent(run { Modifier })
      }

      @Composable
      fun AliasContent(loading: Boolean, modifier: Modifier = Modifier) {
        val fresh = run { Modifier }
        if (loading) LoadingContent(modifier) else LoadedContent(fresh)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(2)
  }

  @Test
  fun `distinguishes forwarded and fresh labeled run results`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) LoadingContent(modifier) else LoadedContent(run { return@run modifier })
      }

      @Composable
      fun FreshContent(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) LoadingContent(modifier) else LoadedContent(run { return@run Modifier })
      }

      @Composable
      fun EarlyReturnContent(loading: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
        if (loading) LoadingContent(modifier)
        else LoadedContent(run {
          if (enabled) return@run Modifier
          modifier
        })
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(2)
  }

  @Test
  fun `does not infer modifier forwarding from a custom run function`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      fun run(block: () -> Modifier): Modifier = Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) LoadingContent(modifier) else LoadedContent(run { modifier })
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports missing forwarding after an uninvoked local function`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        @Composable fun Header() { LoadingContent() }
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports when an explicit conditional can replace the modifier`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        enabled: Boolean,
        modifier: Modifier = Modifier,
      ) {
        if (loading) LoadingContent(if (enabled) modifier else Modifier)
        else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `allows an explicit conditional that always forwards the modifier`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        enabled: Boolean,
        modifier: Modifier = Modifier,
      ) {
        if (loading) LoadingContent(if (enabled) modifier else modifier)
        else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `reports when a conditional alias can replace the modifier`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        enabled: Boolean,
        modifier: Modifier = Modifier,
      ) {
        val branchModifier = if (enabled) modifier else Modifier
        if (loading) LoadingContent(branchModifier) else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `allows a conditional alias that always forwards the modifier`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        enabled: Boolean,
        modifier: Modifier = Modifier,
      ) {
        val branchModifier = if (enabled) modifier else modifier.then(Modifier)
        if (loading) LoadingContent(branchModifier) else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `allows a modifier alias whose other branch returns`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        enabled: Boolean,
        modifier: Modifier = Modifier,
      ) {
        val branchModifier = if (enabled) modifier else return
        if (loading) LoadingContent(branchModifier) else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `allows a modifier expression whose other branch throws`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        enabled: Boolean,
        modifier: Modifier = Modifier,
      ) {
        if (loading) LoadingContent(if (enabled) modifier else error("disabled"))
        else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `ignores a branch call whose modifier argument does not complete`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) LoadingContent(error("unreachable")) else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `allows a branch whose composable does not accept a modifier`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) {
          LoadingContent()
        } else {
          LoadedContent(modifier)
        }
      }

      @Composable fun LoadingContent() = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `reports a branch whose modifier parameter has a different name`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(rootModifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `allows every branch to use its modifier default`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) LoadingContent() else LoadedContent()
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `allows conditionals nested inside another composable`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        Box(modifier) {
          if (loading) LoadingContent() else LoadedContent()
        }
      }

      @Composable
      fun Box(modifier: Modifier = Modifier, content: @Composable () -> Unit) = content()

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `does not require slot values to be forwarded across branches`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
      ) {
        if (loading) {
          LoadingContent(modifier, content)
        } else {
          LoadedContent(modifier)
        }
      }

      @Composable
      fun LoadingContent(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit = {},
      ) = content()

      @Composable
      fun LoadedContent(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit = {},
      ) = content()
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `reports a modifier not forwarded to a branch slot`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        loadingContent: @Composable (Modifier) -> Unit,
        loadedContent: @Composable (Modifier) -> Unit,
      ) {
        if (loading) loadingContent(Modifier) else loadedContent(modifier)
      }
      """
        .trimIndent()

    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
        src/test.kt:11: Error: One branch passes the modifier parameter, but this branch does not. Pass it to every branch whose composable accepts a modifier.

        See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
          if (loading) loadingContent(Modifier) else loadedContent(modifier)
                       ~~~~~~~~~~~~~~
        1 error
        """
          .trimIndent()
      )
  }

  @Test
  fun `allows forwarding the modifier to every branch slot`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        loadingContent: @Composable (Modifier) -> Unit,
        loadedContent: @Composable (Modifier) -> Unit,
      ) {
        if (loading) loadingContent(modifier) else loadedContent(modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `reports modifier reuse through sequential slot invocations`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        modifier: Modifier = Modifier,
        content: @Composable (Modifier) -> Unit,
      ) {
        content(modifier)
        content(modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectReusedModifierErrors(2)
  }

  @Test
  fun `reports modifier reuse through sequential explicit slot invocations`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        modifier: Modifier = Modifier,
        content: @Composable (Modifier) -> Unit,
      ) {
        content.invoke(modifier)
        content.invoke(modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectReusedModifierErrors(2)
  }

  @Test
  fun `reports a modifier not forwarded to a branch slot invoked explicitly`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        loadingContent: @Composable (Modifier) -> Unit,
        loadedContent: @Composable (Modifier) -> Unit,
      ) {
        if (loading) (loadingContent).invoke(Modifier) else loadedContent.invoke(modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `allows forwarding the modifier to explicitly invoked branch slots`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        loadingContent: @Composable (Modifier) -> Unit,
        loadedContent: @Composable (Modifier) -> Unit,
      ) {
        if (loading) loadingContent.invoke(modifier) else loadedContent.invoke(modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `ignores a user defined invoke overload on a composable slot`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      typealias ContentSlot = @Composable (Modifier) -> Unit

      fun ContentSlot.invoke(label: String) = Unit

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        loadingContent: ContentSlot,
        loadedContent: ContentSlot,
      ) {
        if (loading) loadingContent.invoke("loading") else loadedContent(modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `maps modifier arguments after an extension function type receiver`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        loadingContent: @Composable Modifier.(Modifier) -> Unit,
        loadedContent: @Composable Modifier.(Modifier) -> Unit,
      ) {
        if (loading) loadingContent(Modifier, Modifier)
        else loadedContent(Modifier, modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `maps modifier arguments for an extension slot with an implicit receiver`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Modifier.Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        loadingContent: @Composable Modifier.(Modifier) -> Unit,
        loadedContent: @Composable Modifier.(Modifier) -> Unit,
      ) {
        if (loading) loadingContent(Modifier) else loadedContent(modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `maps a modifier argument before a trailing lambda slot argument`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        loadingContent: @Composable (Modifier, @Composable () -> Unit) -> Unit,
        loadedContent: @Composable (Modifier, @Composable () -> Unit) -> Unit,
      ) {
        if (loading) loadingContent(Modifier) {} else loadedContent(modifier) {}
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `maps modifier arguments after a function type context parameter`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      class Scope

      @Composable
      context(scope: Scope)
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        loadingContent: @Composable context(Scope) (Modifier) -> Unit,
        loadedContent: @Composable context(Scope) (Modifier) -> Unit,
      ) {
        if (loading) loadingContent(Modifier) else loadedContent(modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `follows a modifier alias when reporting a context composable branch`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      class Scope

      @Composable
      context(scope: Scope)
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        val forwardedModifier = modifier.then(Modifier)
        if (loading) LoadingContent() else LoadedContent(forwardedModifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports modifier reuse in a composable with a context parameter`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      class Scope

      @Composable
      context(scope: Scope)
      fun Content(modifier: Modifier = Modifier) {
        val firstAlias = modifier
        val secondAlias = firstAlias.then(Modifier)
        FirstContent(secondAlias)
        SecondContent(secondAlias)
      }

      @Composable fun FirstContent(modifier: Modifier = Modifier) = Unit
      @Composable fun SecondContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectReusedModifierErrors(2)
  }

  @Test
  fun `does not confuse context modifier aliases with the value parameter`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      context(contextModifier: Modifier)
      fun Content(modifier: Modifier = Modifier) {
        val contextAlias = contextModifier
        FirstContent(contextAlias)
        SecondContent(contextAlias)
      }

      @Composable fun FirstContent(modifier: Modifier = Modifier) = Unit
      @Composable fun SecondContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `does not treat side effect modifier reads as aliases`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      class Scope

      @Composable
      context(scope: Scope)
      fun Content(modifier: Modifier = Modifier) {
        val conditionalFresh = if (modifier == Modifier) Modifier else Modifier
        val callbackFresh = Modifier.also { println(modifier) }
        FirstContent(conditionalFresh)
        SecondContent(callbackFresh)
      }

      @Composable fun FirstContent(modifier: Modifier = Modifier) = Unit
      @Composable fun SecondContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `reports modifier reuse by value composables with a context parameter`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      class Scope

      @Composable
      context(scope: Scope)
      fun Content(modifier: Modifier = Modifier) {
        val first = FirstContent(modifier)
        val second = SecondContent(modifier)
      }

      @Composable fun FirstContent(modifier: Modifier = Modifier): Int = 1
      @Composable fun SecondContent(modifier: Modifier = Modifier): Int = 2
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectReusedModifierErrors(2)
  }

  @Test
  fun `reports a modifier not forwarded to the same slot in another branch`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable (Modifier) -> Unit,
      ) {
        if (loading) content(Modifier) else content(modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports a modifier not forwarded to the same explicitly invoked slot`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable (Modifier) -> Unit,
      ) {
        if (loading) content.invoke(Modifier) else content.invoke(modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `allows forwarding a modifier to the same slot in every branch`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable (Modifier) -> Unit,
      ) {
        if (loading) content(modifier) else content.invoke(modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `reports when one branch calls a nullable modifier slot without the modifier`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(
        loading: Boolean,
        modifier: Modifier = Modifier,
        content: (@Composable (Modifier) -> Unit)?,
      ) {
        if (loading) content?.invoke(Modifier) else content?.invoke(modifier)
      }
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `allows branches that do not emit modifier-accepting content`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.ReadOnlyComposable
      import androidx.compose.ui.Modifier

      @Composable
      fun Content(state: Int, modifier: Modifier = Modifier) {
        when (state) {
          0 -> Modifier.ExtensionContent()
          1 -> ValueContent()
          2 -> ReadOnlyContent()
          3 -> NullableUnitContent()
          4 -> PlainContent()
          5 -> GenericValueContent(Unit)
          else -> LoadedContent(modifier)
        }
      }

      @Composable fun Modifier.ExtensionContent() = Unit
      @Composable fun ValueContent(modifier: Modifier = Modifier): Int = 1
      @Composable @ReadOnlyComposable fun ReadOnlyContent(modifier: Modifier = Modifier) = Unit
      @Composable fun NullableUnitContent(modifier: Modifier = Modifier): Unit? = Unit
      fun PlainContent(modifier: Modifier = Modifier) = Unit
      @Composable fun <T> GenericValueContent(value: T, modifier: Modifier = Modifier): T = value
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()
    val readOnlyComposable =
      kotlin(
        """
        package androidx.compose.runtime

        annotation class ReadOnlyComposable
        """
          .trimIndent()
      )

    lint().files(*commonStubs, readOnlyComposable, kotlin(code)).run().expectClean()
  }

  @Test
  fun `ignores context modifier parameters on the enclosing composable`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      context(modifier: Modifier)
      fun Content(loading: Boolean) {
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: Modifier = Modifier) = Unit
      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `ignores a modifier context parameter on a branch composable`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      context(contextModifier: Modifier)
      fun Content(loading: Boolean, modifier: Modifier = Modifier) {
        if (loading) ContextContent() else LoadedContent(modifier)
      }

      @Composable
      context(modifier: Modifier)
      fun ContextContent() = Unit

      @Composable fun LoadedContent(modifier: Modifier = Modifier) = Unit
      """
        .trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `reports missing Glance modifiers`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.glance.GlanceModifier

      @Composable
      fun Content(loading: Boolean, modifier: GlanceModifier = GlanceModifier) {
        if (loading) LoadingContent() else LoadedContent(modifier)
      }

      @Composable fun LoadingContent(modifier: GlanceModifier = GlanceModifier) = Unit
      @Composable fun LoadedContent(modifier: GlanceModifier = GlanceModifier) = Unit
      """
        .trimIndent()
    val glanceModifier =
      kotlin(
        """
        package androidx.glance

        interface GlanceModifier {
          companion object : GlanceModifier
        }
        """
          .trimIndent()
      )

    lint().files(*commonStubs, glanceModifier, kotlin(code)).run().expectNotForwardedErrors(1)
  }

  @Test
  fun `reports missing modifiers for compiled composables`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier
      import compiled.CompiledBranches
      import compiled.State

      @Composable
      fun Content(
        state: State,
        branches: CompiledBranches,
        fallbackModifier: Modifier,
        modifier: Modifier = Modifier,
      ) {
        when (state) {
          State.Loading -> branches.loadingContent("loading", fallbackModifier)
          State.Loaded -> branches.loadedContent("loaded", modifier)
        }
      }
      """
        .trimIndent()

    lint()
      .files(compiledBranches, kotlin(code))
      .allowKotlinClassStubs(true)
      .run()
      .expect(
        """
        src/test.kt:14: Error: One branch passes the modifier parameter, but this branch does not. Pass it to every branch whose composable accepts a modifier.

        See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
            State.Loading -> branches.loadingContent("loading", fallbackModifier)
                                      ~~~~~~~~~~~~~~
        1 error
        """
          .trimIndent()
      )
  }
}
