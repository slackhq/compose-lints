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
  fun `errors when the slot parameter passed to other composables in more than one place`() {
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
        )

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
  fun `errors when the slot parameter is passed to another composables and called directly`() {
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
        )

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
  fun `passes when using same name is used as a different function`() {
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

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `passes when using same slot name is used with a different receiver`() {
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

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `passes when passing slot parameter to different composable transformations`() {
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

    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }
}
