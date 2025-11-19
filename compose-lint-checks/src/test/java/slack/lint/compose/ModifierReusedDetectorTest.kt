// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.google.common.truth.Truth
import java.util.SortedMap
import org.intellij.lang.annotations.Language
import org.junit.Test

class ModifierReusedDetectorTest : BaseComposeLintTest() {

  override fun getDetector(): Detector = ModifierReusedDetector()

  override fun getIssues(): List<Issue> = listOf(ModifierReusedDetector.ISSUE)

  override fun tearDown() {
    ModifierReusedDetector.testCodeGraph = false
    ModifierReusedDetector.codeFlowGraph = emptyMap()
  }

  @Test
  fun `errors when the modifier parameter of a Composable is used more than once by siblings or parent-children`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun Something(modifier: Modifier) {
            Row(modifier) {
                OtherComposable(modifier)
            }
        }
        @Composable
        fun Something(modifier: Modifier): Int {
            Column(modifier = modifier) {
                OtherComposable(modifier = Modifier)
                OtherComposable(modifier = modifier)
            }
        }
        @Composable
        fun BoxScope.Something(modifier: Modifier) {
            Column(modifier = modifier) {
                OtherComposable()
            }
            OtherComposable(modifier = modifier)
            OtherComposable(modifier = modifier.padding())
        }
        @Composable
        fun Something(myMod: Modifier) {
            Column {
                OtherComposable(myMod)
                OtherComposable(myMod)
            }
        }
        @Composable
        fun FoundThisOneInTheWild(modifier: Modifier = Modifier) {
            Box(
                modifier = modifier
                    .size(10)
                    .then(Modifier)
            ) {
                Box(
                    modifier = modifier.padding()
                )
            }
        }
        private fun Modifier.size(int: Int): Modifier = this
      """
        .trimIndent()

    lint()
      .files(*commonStubs, *specificStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:6: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Row(modifier) {
                  ~~~~~~~~
          src/test.kt:7: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(modifier)
                                  ~~~~~~~~
          src/test.kt:12: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
                                ~~~~~~~~
          src/test.kt:14: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(modifier = modifier)
                                             ~~~~~~~~
          src/test.kt:19: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
                                ~~~~~~~~
          src/test.kt:22: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              OtherComposable(modifier = modifier)
                                         ~~~~~~~~
          src/test.kt:23: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              OtherComposable(modifier = modifier.padding())
                                         ~~~~~~~~
          src/test.kt:28: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(myMod)
                                  ~~~~~
          src/test.kt:29: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(myMod)
                                  ~~~~~
          src/test.kt:35: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  modifier = modifier
                             ~~~~~~~~
          src/test.kt:40: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                      modifier = modifier.padding()
                                 ~~~~~~~~
          11 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors when the modifier parameter of a Composable is tweaked or reassigned and reused`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.foundation.layout.fillMaxWidth

        @Composable
        fun Something1(modifier: Modifier) {
            Column(modifier = modifier) {
                OtherComposable(modifier = modifier.fillMaxWidth())
            }
        }
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier = modifier) {
                val newModifier = modifier.fillMaxWidth()
                OtherComposable(modifier = newModifier)
            }
        }
        @Composable
        fun Something(modifier: Modifier) {
            val newModifier = modifier.fillMaxWidth()
            Column(modifier = modifier) {
                OtherComposable(modifier = newModifier)
            }
        }
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier = modifier) {
                OtherComposable(modifier = Modifier.then(modifier))
            }
        }
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier = modifier) {
                OtherComposable(modifier = Modifier.then(modifier).fillMaxWidth())
                OtherComposable(modifier = Modifier.fillMaxWidth().then(modifier))
            }
        }
      """
        .trimIndent()

    lint()
      .files(*commonStubs, *specificStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:7: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
                                ~~~~~~~~
          src/test.kt:8: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(modifier = modifier.fillMaxWidth())
                                             ~~~~~~~~
          src/test.kt:13: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
                                ~~~~~~~~
          src/test.kt:15: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(modifier = newModifier)
                                             ~~~~~~~~~~~
          src/test.kt:21: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
                                ~~~~~~~~
          src/test.kt:22: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(modifier = newModifier)
                                             ~~~~~~~~~~~
          src/test.kt:27: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
                                ~~~~~~~~
          src/test.kt:28: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(modifier = Modifier.then(modifier))
                                                           ~~~~~~~~
          src/test.kt:33: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
                                ~~~~~~~~
          src/test.kt:34: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(modifier = Modifier.then(modifier).fillMaxWidth())
                                                           ~~~~~~~~
          src/test.kt:35: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(modifier = Modifier.fillMaxWidth().then(modifier))
                                                                          ~~~~~~~~
          11 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors when multiple Composables use the modifier even when it's been assigned to a new val`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.foundation.layout.fillMaxWidth

        @Composable
        fun Something(modifier: Modifier) {
            val tweakedModifier = Modifier.then(modifier).fillMaxWidth()
            val reassignedModifier = modifier
            val modifier3 = Modifier.fillMaxWidth()
            Column(modifier = modifier) {
                OtherComposable(modifier = newModifier)
                OtherComposable(modifier = tweakedModifier)
                OtherComposable(modifier = reassignedModifier)
                OtherComposable(modifier = modifier3) // ok
            }
            OtherComposable(modifier = tweakedModifier)
        }
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier = modifier) {
                val tweakedModifier = Modifier.then(modifier).fillMaxWidth()
                val reassignedModifier = modifier
                OtherComposable(modifier = newModifier)
                OtherComposable(modifier = tweakedModifier)
                OtherComposable(modifier = reassignedModifier)
            }
        }

        val newModifier = Modifier
      """
        .trimIndent()

    lint()
      .files(*commonStubs, *specificStubs, kotlin(code))
      .run()
      .expect(
        """
          src/test.kt:10: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
                                ~~~~~~~~
          src/test.kt:12: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(modifier = tweakedModifier)
                                             ~~~~~~~~~~~~~~~
          src/test.kt:13: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(modifier = reassignedModifier)
                                             ~~~~~~~~~~~~~~~~~~
          src/test.kt:16: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              OtherComposable(modifier = tweakedModifier)
                                         ~~~~~~~~~~~~~~~
          src/test.kt:20: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
                                ~~~~~~~~
          src/test.kt:24: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(modifier = tweakedModifier)
                                             ~~~~~~~~~~~~~~~
          src/test.kt:25: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth(). Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  OtherComposable(modifier = reassignedModifier)
                                             ~~~~~~~~~~~~~~~~~~
          7 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `passes when a Composable only passes its modifier parameter to the root level layout`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.foundation.layout.fillMaxWidth
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.Text

        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier) {
                OtherComposable()
                Text("Hi")
            }
        }
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier) {
                OtherComposable(Modifier.fillMaxWidth())
                Text("Hi", modifier = Modifier.padding())
            }
        }
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier) {
                val newModifier = Modifier.weight(1f)
                OtherComposable(newModifier)
                Text("Hi")
            }
        }
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier) {
                val newModifier = Modifier.weight(1f)
                if (someCondition) {
                    OtherComposable(newModifier)
                } else {
                    OtherComposable(newModifier)
                }
            }
        }
      """
        .trimIndent()
    lint().files(*commonStubs, *specificStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `passes when modifiers are reused for mutually exclusive branches`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun Something(modifier: Modifier = Modifier) {
            if (someCondition) {
                OtherComposable(modifier = modifier.fillMaxWidth())
            } else {
                OtherComposable(modifier)
            }
        }
        @Composable
        fun Something(modifier: Modifier = Modifier) {
            if (someCondition) {
                OtherComposable(modifier = modifier.fillMaxWidth())
                return
            }
            OtherComposable(modifier)
        }
        @Composable
        fun Something(modifier: Modifier = Modifier) {
            if (someCondition) OtherComposable(modifier = modifier.fillMaxWidth())
            else OtherComposable(modifier)
        }
      """
        .trimIndent()
    lint().files(*commonStubs, *specificStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `passes when used on vals with lambdas`() {
    @Language("kotlin")
    val code =
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        private fun TrustedFriendsMembersAppBar(
            onBackClicked: () -> Unit,
            onDoneClicked: () -> Unit,
        ) = Unit

        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier = modifier) {
                TrustedFriendsMembersAppBar(
                    onBackClicked = { println("viewModel.processUserIntent(BackClicked)") },
                    onDoneClicked = { println("viewModel.processUserIntent(DoneClicked)") }
                )

                val recommendedEmptyUsersContent: @Composable ((Modifier) -> Unit)? = when {
                    false -> null
                    true -> { localModifier: Modifier ->
                        Box(modifier = localModifier)
                    }
                    else -> { localModifier ->
                        Box(modifier = localModifier)
                    }
                }
            }
        }
      """
        .trimIndent()
    lint().files(*commonStubs, *specificStubs, kotlin(code)).run().expectClean()
  }

  private val specificStubs =
    arrayOf(
      kotlin(
        """
        package androidx.compose.foundation.layout
        import androidx.compose.ui.Modifier
        fun Modifier.fillMaxWidth(): Modifier = this
        fun Modifier.padding(): Modifier = this
      """
          .trimIndent()
      ),
      kotlin(
        """
          import androidx.compose.ui.Modifier
          import androidx.compose.runtime.Composable

          object BoxScope

          @Composable
          fun Box(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) { }

          @Composable
          fun Column(modifier: Modifier = Modifier, content: @Composable () -> Unit) { }

          @Composable
          fun Row(modifier: Modifier = Modifier, content: @Composable () -> Unit) { }

          @Composable
          fun OtherComposable(modifier: Modifier = Modifier, content: @Composable () -> Unit) { }

          val someCondition: Boolean get() = TODO()
        """
          .trimIndent()
      ),
    )

  @Test
  fun `code flow analysis - simple linear code`() {
    ModifierReusedDetector.testCodeGraph = true
    lint()
      .testModes(TestMode.DEFAULT)
      .files(
        *commonStubs,
        *specificStubs,
        kotlin(
          """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun CodeFlowAnalysisTest(modifier: Modifier = Modifier) {
            val irrelevantCalculation = 123 + 456
            run { "Hello".let { it + it } }
            OtherComposable(modifier) // Node 8
        }
      """
            .trimIndent()
        ),
      )
      .run()

    ModifierReusedDetector.codeFlowGraph.assertEqualTo(
      // "Node start" and "Node end" represent method enter and method exit respectively
      // "Node 8" means - there was @Composable call on line 8 that used modifier parameter
      "Node start" to setOf("Node 8"),
      "Node 8" to setOf("Node end"),
    )
  }

  @Test
  fun `code flow analysis - if-else`() {
    ModifierReusedDetector.testCodeGraph = true
    lint()
      .testModes(TestMode.DEFAULT)
      .files(
        *commonStubs,
        *specificStubs,
        kotlin(
          """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun CodeFlowAnalysisTest(modifier: Modifier = Modifier) {
            if (true) { // Switch 6
                OtherComposable(modifier) // Node 7
            } else {
                OtherComposable(modifier) // Node 9
            }
        }
      """
            .trimIndent()
        ),
      )
      .run()

    ModifierReusedDetector.codeFlowGraph.assertEqualTo(
      "Node start" to setOf("Switch 6"),
      // if statement turns into Switch node, and it always has multiple edges that represent
      // branches
      "Switch 6" to setOf("Node 7", "Node 9"),
      "Node 7" to setOf("Node end"),
      "Node 9" to setOf("Node end"),
    )
  }

  @Test
  fun `code flow analysis - if-else without call`() {
    ModifierReusedDetector.testCodeGraph = true
    lint()
      .testModes(TestMode.DEFAULT)
      .files(
        *commonStubs,
        *specificStubs,
        kotlin(
          """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun CodeFlowAnalysisTest(modifier: Modifier = Modifier) {
            if (true) { // Switch 6
                println("true")
            } else {
                OtherComposable(modifier) // Node 9
            }
        }
      """
            .trimIndent()
        ),
      )
      .run()

    ModifierReusedDetector.codeFlowGraph.assertEqualTo(
      "Node start" to setOf("Switch 6"),
      // Because true branch doesn't have @Composable call, graph skips to the next node, which is
      // method exit
      "Switch 6" to setOf("Node end", "Node 9"),
      "Node 9" to setOf("Node end"),
    )
  }

  @Test
  fun `code flow analysis - guard-if`() {
    ModifierReusedDetector.testCodeGraph = true
    lint()
      .testModes(TestMode.DEFAULT)
      .files(
        *commonStubs,
        *specificStubs,
        kotlin(
          """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun CodeFlowAnalysisTest(modifier: Modifier = Modifier) {
            if (someCondition) { // Switch 6
                OtherComposable(modifier) // Node 7
                return
            }
            OtherComposable(modifier) // Node 10
        }
      """
            .trimIndent()
        ),
      )
      .run()

    ModifierReusedDetector.codeFlowGraph.assertEqualTo(
      "Node start" to setOf("Switch 6"),
      "Switch 6" to setOf("Node 7", "Node 10"),
      // Because of return, after branch method execution goes straight to method end
      "Node 7" to setOf("Node end"),
      "Node 10" to setOf("Node end"),
    )
  }

  @Test
  fun `code flow analysis - if-elseif-else`() {
    ModifierReusedDetector.testCodeGraph = true
    lint()
      .testModes(TestMode.DEFAULT)
      .files(
        *commonStubs,
        *specificStubs,
        kotlin(
          """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun CodeFlowAnalysisTest(modifier: Modifier = Modifier) {
            if (someCondition)  // Switch 6
                OtherComposable(modifier) // Node 7
            else if (someCondition) // Switch 8
                OtherComposable(modifier) // Node 9
            else
                OtherComposable(modifier) // Node 11
        }
      """
            .trimIndent()
        ),
      )
      .run()

    ModifierReusedDetector.codeFlowGraph.assertEqualTo(
      "Node start" to setOf("Switch 6"),
      // First if contains second if
      "Switch 6" to setOf("Node 7", "Switch 8"),
      "Switch 8" to setOf("Node 9", "Node 11"),
      // All nodes go to method exit
      "Node 7" to setOf("Node end"),
      "Node 9" to setOf("Node end"),
      "Node 11" to setOf("Node end"),
    )
  }

  @Test
  fun `code flow analysis - when inside when`() {
    ModifierReusedDetector.testCodeGraph = true
    lint()
      .testModes(TestMode.DEFAULT)
      .files(
        *commonStubs,
        *specificStubs,
        kotlin(
          """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun CodeFlowAnalysisTest(modifier: Modifier = Modifier) {
            when (true) { // Switch 6
                true -> when { // Switch 7
                    123 == 456 -> OtherComposable(modifier) // Node 8
                    false -> OtherComposable(modifier) // Node 9
                }
                false -> when { // Switch 11
                    2 + 2 == 5 -> OtherComposable(modifier) // Node 12
                    else -> OtherComposable(modifier) // Node 13
                }
            }
            OtherComposable(modifier) // Node 16
        }
      """
            .trimIndent()
        ),
      )
      .run()

    ModifierReusedDetector.codeFlowGraph.assertEqualTo(
      "Node start" to setOf("Switch 6"),
      // Note that this Switch has three branches, even though "when" has two and it's exhaustive.
      // We cannot check for exhaustiveness, so there's always an edge that skips "when" entirely.
      "Switch 6" to setOf("Switch 7", "Switch 11", "Node 16"),
      "Switch 7" to setOf("Node 8", "Node 9", "Node 16"),
      "Switch 11" to setOf("Node 12", "Node 13", "Node 16"),
      "Node 8" to setOf("Node 16"),
      "Node 9" to setOf("Node 16"),
      "Node 12" to setOf("Node 16"),
      "Node 13" to setOf("Node 16"),
      "Node 16" to setOf("Node end"),
    )
  }

  @Test
  fun `code flow analysis - modifier reassigned`() {
    ModifierReusedDetector.testCodeGraph = true
    lint()
      .testModes(TestMode.DEFAULT)
      .files(
        *commonStubs,
        *specificStubs,
        kotlin(
          """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        private fun Modifier.background() = this

        @Composable
        fun CodeFlowAnalysisTest(modifier: Modifier = Modifier) {
            val modifier1 = modifier
            val modifier2 = modifier.background()
            val modifier3 = Modifier.then(modifier)
            val modifier4 = Modifier.background().then(modifier2)

            OtherComposable(modifier1) // Node 13
            OtherComposable(modifier2) // Node 14
            OtherComposable(modifier3) // Node 15
            OtherComposable(modifier4) // Node 16
            OtherComposable(modifier.background())  // Node 17
            OtherComposable(Modifier.then(modifier)) // Node 18
            OtherComposable(Modifier.background().then(modifier)) // Node 19
        }
      """
            .trimIndent()
        ),
      )
      .run()

    ModifierReusedDetector.codeFlowGraph.assertEqualTo(
      "Node start" to setOf("Node 13"),
      *(13..19).map { "Node $it" }.zipWithNext().map { (n1, n2) -> n1 to setOf(n2) }.toTypedArray(),
      "Node 19" to setOf("Node end"),
    )
  }

  @Test
  fun `code flow analysis - lambda argument`() {
    ModifierReusedDetector.testCodeGraph = true
    lint()
      .testModes(TestMode.DEFAULT)
      .files(
        *commonStubs,
        *specificStubs,
        kotlin(
          """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        private fun Wrapper(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Unit

        @Composable
        fun CodeFlowAnalysisTest(modifier: Modifier = Modifier) {
            Wrapper {
                OtherComposable(modifier) // Node 10
            }
            Wrapper(content = {
                OtherComposable(modifier)  // Node 13
            })
        }
      """
            .trimIndent()
        ),
      )
      .run()

    ModifierReusedDetector.codeFlowGraph.assertEqualTo(
      "Node start" to setOf("Node 10"),
      "Node 10" to setOf("Node 13"),
      "Node 13" to setOf("Node end"),
    )
  }

  @Test
  fun `code flow analysis - other language constructs don't interfere`() {
    ModifierReusedDetector.testCodeGraph = true
    lint()
      .testModes(TestMode.DEFAULT)
      .files(
        *commonStubs,
        *specificStubs,
        kotlin(
          """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        interface LayoutParams {
            fun measure()
        }
        class View {
            var layoutParams: LayoutParams? = null
        }

        @Composable
        fun CodeFlowAnalysisTest(modifier: Modifier = Modifier) {
            val view = View()
            view.layoutParams = object: LayoutParams {
                override fun measure() = Unit
            }
        }
      """
            .trimIndent()
        ),
      )
      .run()

    ModifierReusedDetector.codeFlowGraph.assertEqualTo("Node start" to setOf("Node end"))
  }

  fun Map<ModifierReusedDetector.GraphNode, Set<ModifierReusedDetector.GraphNode>>.assertEqualTo(
    vararg list: Pair<String, Set<String>>
  ) {
    val graph: SortedMap<String, java.util.HashSet<String>> =
      this.map { (node, links) -> node.toString() to links.mapTo(HashSet()) { it.toString() } }
        .associateTo(sortedMapOf()) { it }
    Truth.assertThat(graph).isEqualTo(list.associateTo(sortedMapOf()) { it })
  }
}
