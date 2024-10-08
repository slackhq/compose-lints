// Copyright (C) 2024 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class SlotReusedDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = SlotReusedDetector()

  override fun getIssues(): List<Issue> = listOf(SlotReusedDetector.ISSUE)

  @Test
  fun `errors when the slot parameter of a Composable is used more than once at the same time`() {
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

    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:7: Error: Slots should be invoked in at most once place to meet lifecycle expectations. Slots should not be invoked in multiple places in source code, where the invoking location changes based on some condition. This will preserve the slot's internal state when the invoking location changes. See https://slackhq.github.io/compose-lints/rules/#do-not-invoke-slots-in-more-than-once-place for more information. [SlotReused]
            slot: @Composable () -> Unit,
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:18: Error: Slots should be invoked in at most once place to meet lifecycle expectations. Slots should not be invoked in multiple places in source code, where the invoking location changes based on some condition. This will preserve the slot's internal state when the invoking location changes. See https://slackhq.github.io/compose-lints/rules/#do-not-invoke-slots-in-more-than-once-place for more information. [SlotReused]
            slot: @Composable () -> Unit,
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          2 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors when the slot parameter of a Composable is used more than once in different branches`() {
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

    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:8: Error: Slots should be invoked in at most once place to meet lifecycle expectations. Slots should not be invoked in multiple places in source code, where the invoking location changes based on some condition. This will preserve the slot's internal state when the invoking location changes. See https://slackhq.github.io/compose-lints/rules/#do-not-invoke-slots-in-more-than-once-place for more information. [SlotReused]
            slot: @Composable () -> Unit,
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          1 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors when slot parameter is used in different movableContentOfs`() {
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
          val movableSlot1 = remember(slot) { movableContentOf { slot() } }
          val movableSlot2 = remember(slot) { movableContentOf { slot() } }
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

    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:8: Error: Slots should be invoked in at most once place to meet lifecycle expectations. Slots should not be invoked in multiple places in source code, where the invoking location changes based on some condition. This will preserve the slot's internal state when the invoking location changes. See https://slackhq.github.io/compose-lints/rules/#do-not-invoke-slots-in-more-than-once-place for more information. [SlotReused]
            slot: @Composable () -> Unit,
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          1 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors when multiple slot parameters of a Composable are used more than once in different branches`() {
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

    lint()
      .files(*commonStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:8: Error: Slots should be invoked in at most once place to meet lifecycle expectations. Slots should not be invoked in multiple places in source code, where the invoking location changes based on some condition. This will preserve the slot's internal state when the invoking location changes. See https://slackhq.github.io/compose-lints/rules/#do-not-invoke-slots-in-more-than-once-place for more information. [SlotReused]
            slot1: @Composable () -> Unit,
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:9: Error: Slots should be invoked in at most once place to meet lifecycle expectations. Slots should not be invoked in multiple places in source code, where the invoking location changes based on some condition. This will preserve the slot's internal state when the invoking location changes. See https://slackhq.github.io/compose-lints/rules/#do-not-invoke-slots-in-more-than-once-place for more information. [SlotReused]
            slot2: @Composable () -> Unit,
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          2 errors, 0 warnings
        """
          .trimIndent()
      )
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

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
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
          val movableSlot = remember(slot) { movableContentOf(slot) }
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

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
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

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `passes with multiple slot parameters`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun SplitLayoutVerticalSimple(
            first: @Composable () -> Unit,
            modifier: Modifier = Modifier,
            second: @Composable () -> Unit
        ) {
          Layout(
            modifier = modifier.clipToBounds(),
            content = {
              Box(
                  Modifier
                      .layoutId("first")
                      .consumeWindowInsets(
                          WindowInsets.safeDrawing.only(WindowInsetsSides.End)
                      )
              ) {
                  first()
              }
              Box(
                  Modifier
                      .layoutId("second")
                      .consumeWindowInsets(
                          WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
                      )
              ) {
                  second()
              }
            }
          ) { measurable, constraints ->
            layout(constraints.maxWidth, constraints.maxHeight) {}
          }
        }
        """.trimIndent()

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }
}
