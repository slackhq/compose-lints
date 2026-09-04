// Copyright (C) 2024 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class SlotReusedDetectorTest : BaseComposeLintTest() {

  private val layoutStubs =
    kotlin(
        """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun Box(modifier: Modifier = Modifier, content: @Composable () -> Unit) = content()

        @Composable
        fun Row(modifier: Modifier = Modifier, content: @Composable () -> Unit) = content()

        @Composable
        fun Column(modifier: Modifier = Modifier, content: @Composable () -> Unit) = content()

        @Composable
        fun Spacer(modifier: Modifier = Modifier) = Unit
        """
          .trimIndent()
      )
      .to("src/LayoutStubs.kt")

  private fun lint(code: String): TestLintTask =
    lint().files(*commonStubs, layoutStubs, kotlin(code))

  private fun TestLintResult.expectSlotReusedErrors(count: Int) {
    expectErrorCount(count)
    val diagnostic =
      Regex.escape(
        "Error: Slots should be invoked at most once on each execution path to meet lifecycle expectations."
      ) + ".*?\\[SlotReused]"
    expectMatches("(?s)(?:.*?$diagnostic){$count}.*")
  }

  override fun getDetector(): Detector = SlotReusedDetector()

  override fun getIssues(): List<Issue> = listOf(SlotReusedDetector.ISSUE)

  @Test
  fun `reports when a slot is called twice on one path`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        slot: @Composable () -> Unit,
      ) {
        Row(modifier) {
          slot()
          slot()
        }
      }

      @Composable
      fun SomethingElse(
        modifier: Modifier = Modifier,
        slot: @Composable () -> Unit,
      ) {
        Column(modifier) {
          slot()
          Box {
            slot()
          }
        }
      }
      """
        .trimIndent()

    lint(code)
      .run()
      .expect(
        """
        src/test.kt:7: Error: Slots should be invoked at most once on each execution path to meet lifecycle expectations. For example, calling a slot inside a branch and again afterward can compose two copies of the slot's content and internal state. Calling it once in each mutually exclusive branch is allowed because only one branch runs.

        See https://slackhq.github.io/compose-lints/rules/#do-not-invoke-slots-in-more-than-once-place for more information. [SlotReused]
          slot: @Composable () -> Unit,
          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test.kt:18: Error: Slots should be invoked at most once on each execution path to meet lifecycle expectations. For example, calling a slot inside a branch and again afterward can compose two copies of the slot's content and internal state. Calling it once in each mutually exclusive branch is allowed because only one branch runs.

        See https://slackhq.github.io/compose-lints/rules/#do-not-invoke-slots-in-more-than-once-place for more information. [SlotReused]
          slot: @Composable () -> Unit,
          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        2 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `allows the slot parameter in mutually exclusive branches`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        flag: Boolean,
        slot: @Composable () -> Unit,
      ) {
        Box(modifier) {
          if (flag) {
            slot()
          } else {
            slot()
          }
        }
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows an explicitly invoked slot in mutually exclusive branches`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        flag: Boolean,
        content: @Composable (Modifier) -> Unit,
      ) {
        if (flag) {
          content.invoke(Modifier)
        } else {
          content.invoke(modifier)
        }
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows a nullable slot in mutually exclusive branches`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(
        flag: Boolean,
        content: (@Composable () -> Unit)?,
      ) {
        if (flag) {
          content?.invoke()
        } else {
          content?.invoke()
        }
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `errors when an explicitly invoked slot is used sequentially`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(content: @Composable () -> Unit) {
        content.invoke()
        content.invoke()
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `errors when a nullable slot is invoked sequentially`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(content: (@Composable () -> Unit)?) {
        content?.invoke()
        content?.invoke()
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `reports when a branch slot call can reach a later call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(flag: Boolean, content: @Composable () -> Unit) {
        if (flag) {
          content()
        }
        content()
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `allows a branch slot call that returns before a later call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(flag: Boolean, content: @Composable () -> Unit) {
        if (flag) {
          content()
          return
        }
        content()
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows a branch slot call that throws before a later call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(flag: Boolean, content: @Composable () -> Unit) {
        if (flag) {
          content()
          throw IllegalStateException("stops here")
        }
        content()
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows a branch slot call followed by a Nothing call before a later call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(flag: Boolean, content: @Composable () -> Unit) {
        if (flag) {
          content()
          error("stops here")
        }
        content()
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows a non-local return after a slot call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(flag: Boolean, content: @Composable () -> Unit) {
        run {
          if (flag) {
            content()
            return
          }
        }
        content()
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows slot calls in mutually exclusive when branches`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(state: Int, content: @Composable () -> Unit) {
        when (state) {
          0 -> content()
          1 -> content.invoke()
          else -> Unit
        }
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `reports when a when branch slot call can reach a later call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(state: Int, content: @Composable () -> Unit) {
        when (state) {
          0 -> content()
          else -> Unit
        }
        content()
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `reports sequential slot calls in an expression body`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(content: @Composable () -> Unit) = Wrapper {
        content()
        content()
      }

      @Composable
      fun Wrapper(content: @Composable () -> Unit) = content()
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `allows passing the slot to composables in mutually exclusive branches`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        flag: Boolean,
        slot: @Composable () -> Unit,
      ) {
        Box(modifier) {
          if (flag) {
            AnotherThing(slot = slot)
          } else {
            AnotherThing(slot = slot)
          }
        }
      }

      @Composable
      fun AnotherThing(
        modifier: Modifier = Modifier,
        slot: @Composable () -> Unit,
      ) = Unit

      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows passing or invoking the slot in mutually exclusive branches`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        flag: Boolean,
        slot: @Composable () -> Unit,
      ) {
        Box(modifier) {
          if (flag) {
            AnotherThing(slot = slot)
          } else {
            slot()
          }
        }
      }

      @Composable
      fun AnotherThing(
        modifier: Modifier = Modifier,
        slot: @Composable () -> Unit,
      ) = Unit

      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `errors when the slot is passed and invoked sequentially`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        AnotherThing(slot = slot)
        slot()
      }

      @Composable
      fun AnotherThing(slot: @Composable () -> Unit) = slot()
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `errors when the slot is passed to two parameters of one call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        AnotherThing(first = slot, second = slot)
      }

      @Composable
      fun AnotherThing(
        first: @Composable () -> Unit,
        second: @Composable () -> Unit,
      ) {
        first()
        second()
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `errors when the slot is invoked by two literal parameters of one call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        AnotherThing(first = { slot() }, second = { slot() })
      }

      @Composable
      fun AnotherThing(
        first: @Composable () -> Unit,
        second: @Composable () -> Unit,
      ) {
        first()
        second()
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `allows slot parameter in movable content invoked in mutually exclusive branches`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.movableContentOf
      import androidx.compose.runtime.remember
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        flag: Boolean,
        slot: @Composable () -> Unit,
      ) {
        val movableSlot1 = remember { movableContentOf { slot() } }
        val movableSlot2 = remember { movableContentOf { slot() } }
        Box(modifier) {
          if (flag) {
            movableSlot1()
          } else {
            movableSlot2()
          }
        }
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows multiple slot parameters in mutually exclusive branches`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        flag: Boolean,
        slot1: @Composable () -> Unit,
        slot2: @Composable () -> Unit,
      ) {
        Box(modifier) {
          if (flag) {
            slot1()
            slot2()
          } else {
            slot1()
            slot2()
          }
        }
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `passes when the slot parameter is shadowed`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        flag: Boolean,
        slot: @Composable () -> Unit,
      ) {
        Box(modifier) {
          slot()
          SomethingElse { slot ->
            slot()
          }
        }
      }

      @Composable
      fun SomethingElse(
        modifier: Modifier = Modifier,
        content: @Composable (@Composable () -> Unit) -> Unit,
      ) {
        Box(modifier) {
          content {
            Spacer()
          }
        }
      }


      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `passes when using movableContentOf for the slot parameter`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.movableContentOf
      import androidx.compose.runtime.remember
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        flag: Boolean,
        slot: @Composable () -> Unit,
      ) {
        val movableSlot = remember { movableContentOf(slot) }
        Box(modifier) {
          if (flag) {
            movableSlot()
          } else {
            movableSlot()
          }
        }
      }

      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `passes when using slot parameter in only one branch`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        flag: Boolean,
        slot: @Composable () -> Unit,
      ) {
        Box(modifier) {
          if (flag) {
            slot()
          } else {
            Spacer()
          }
        }
      }

      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows the same name on a different function`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        first: @Composable () -> Unit,
      ) {
        Box(modifier) {
          first()
          listOf("1").first { it == "2" }
        }
      }

      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows the same slot name on a different receiver`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      class Section(
        // other stuff
        val content: @Composable () -> Unit,
      )

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        section: Section? = null,
        content: @Composable () -> Unit,
      ) {
        Box(modifier) {
          content()
          section?.content()
        }
      }

      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows mutually exclusive slot transformations`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier

      @Composable
      fun Something(
        modifier: Modifier = Modifier,
        flag: Boolean,
        slot: @Composable () -> Unit,
      ) {
        val transformedSlot = if (flag) {
          transformationA(slot)
        } else {
          transformationB(slot)
        }

        Box(modifier) {
          transformedSlot()
        }
      }

      @Composable
      fun transformationA(slot: @Composable () -> Unit): @Composable () -> Unit = slot

      @Composable
      fun transformationB(slot: @Composable () -> Unit): @Composable () -> Unit = slot

      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows stored slot lambdas called in different branches`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      class Holder(val content: @Composable () -> Unit)

      @Composable
      fun Something(flag: Boolean, slot: @Composable () -> Unit) {
        val first = Holder { slot() }
        val second = Holder { slot() }
        if (flag) {
          first.content()
        } else {
          second.content()
        }
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `ignores a throw inside a stored lambda`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      class Holder(val content: @Composable () -> Unit)

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        slot()
        Holder { throw IllegalStateException() }
        slot()
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `reports a slot call inside a while loop`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(keepGoing: Boolean, slot: @Composable () -> Unit) {
        while (keepGoing) {
          slot()
        }
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `reports a slot call inside a for loop`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(values: List<Int>, slot: @Composable () -> Unit) {
        for (value in values) {
          slot()
        }
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `reports repeat with count 2`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        repeat(2) {
          slot()
        }
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `allows repeat with a negative count before a slot call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        repeat(-1) {
          slot()
        }
        slot()
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows repeat with count 1`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        repeat(1) {
          slot()
        }
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows repeat with action first and count 1`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        repeat(action = { slot() }, times = 1)
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows repeat with action first and count 0`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        repeat(action = { slot() }, times = 0)
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `reports repeat with action first and count 2`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        repeat(action = { slot() }, times = 2)
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `reports repeat with a nonconstant count`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(times: Int, slot: @Composable () -> Unit) {
        repeat(times) {
          slot()
        }
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `allows repeat with a parenthesized action and count 0 before a slot call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        repeat(action = ({ slot() }), times = 0)
        slot()
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows repeat stopped by a non-local return`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        repeat(2) {
          slot()
          return
        }
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows repeat that returns before a later slot call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        slot()
        repeat(2) {
          return
        }
        slot()
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `reports repeat with a local return after the slot call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        repeat(2) {
          slot()
          return@repeat
        }
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `allows repeat when only paths without slot calls can continue`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(flag: Boolean, slot: @Composable () -> Unit) {
        repeat(2) {
          if (flag) {
            slot()
            return
          }
        }
        slot()
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows an exactly once lambda that returns before a later slot call`() {
    // Wildcard contract imports below prevent lint's alias mode from renaming the contract DSL
    // call.
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import kotlin.contracts.*

      @OptIn(ExperimentalContracts::class)
      inline fun once(action: () -> Unit) {
        contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
        action()
      }

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        slot()
        once { return }
        slot()
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `reports slot calls when an at most once lambda can skip a return`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import kotlin.contracts.*

      @OptIn(ExperimentalContracts::class)
      inline fun maybe(run: Boolean, action: () -> Unit) {
        contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
        if (run) action()
      }

      @Composable
      fun Something(flag: Boolean, slot: @Composable () -> Unit) {
        slot()
        maybe(flag) { return }
        slot()
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `reports a slot call with an at least once contract`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import kotlin.contracts.*

      @OptIn(ExperimentalContracts::class)
      inline fun twice(action: () -> Unit) {
        contract { callsInPlace(action, InvocationKind.AT_LEAST_ONCE) }
        action()
        action()
      }

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        twice { slot() }
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `allows a lambda slot call that throws before a later call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(flag: Boolean, slot: @Composable () -> Unit) {
        run {
          if (flag) {
            slot()
            throw IllegalStateException("stops here")
          }
        }
        slot()
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `allows a lambda slot call that terminates before a later call`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(flag: Boolean, slot: @Composable () -> Unit) {
        run {
          if (flag) {
            slot()
            error("stops here")
          }
        }
        slot()
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `reports repeated slot calls in a lambda that never completes`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        run {
          while (true) {
            slot()
          }
        }
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `allows local composable lambdas invoked in different branches`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(flag: Boolean, slot: @Composable () -> Unit) {
        val first: @Composable () -> Unit = { slot() }
        val second: @Composable () -> Unit = { slot() }
        if (flag) {
          first()
        } else {
          second()
        }
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }

  @Test
  fun `errors when a local composable lambda is invoked sequentially`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        val local: @Composable () -> Unit = { slot() }
        local()
        local()
      }
      """
        .trimIndent()

    lint(code).run().expectSlotReusedErrors(1)
  }

  @Test
  fun `allows a local composable lambda invoked once`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable

      @Composable
      fun Something(slot: @Composable () -> Unit) {
        val local: @Composable () -> Unit = { slot() }
        local()
      }
      """
        .trimIndent()

    lint(code).run().expectClean()
  }
}
