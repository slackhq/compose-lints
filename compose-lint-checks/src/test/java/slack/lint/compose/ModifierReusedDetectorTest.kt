// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class ModifierReusedDetectorTest : BaseSlackLintTest() {

  override fun getDetector(): Detector = ModifierReusedDetector()
  override fun getIssues(): List<Issue> = listOf(ModifierReusedDetector.ISSUE)

  // This mode is irrelevant to our test and totally untestable with stringy outputs
  override val skipTestModes: Array<TestMode> =
    arrayOf(TestMode.PARENTHESIZED, TestMode.SUPPRESSIBLE, TestMode.TYPE_ALIAS)

  @Test
  fun `errors when the modifier parameter of a Composable is used more than once by siblings or parent-children`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun Something(modifier: Modifier) {
            Row(modifier) {
                SomethingElse(modifier)
            }
        }
        @Composable
        fun Something(modifier: Modifier): Int {
            Column(modifier = modifier) {
                SomethingElse(modifier = Modifier)
                SomethingDifferent(modifier = modifier)
            }
        }
        @Composable
        fun BoxScope.Something(modifier: Modifier) {
            Column(modifier = modifier) {
                SomethingDifferent()
            }
            SomethingElse(modifier = modifier)
            SomethingElse(modifier = modifier.padding12())
        }
        @Composable
        fun Something(myMod: Modifier) {
            Column {
                SomethingElse(myMod)
                SomethingElse(myMod)
            }
        }
        @Composable
        fun FoundThisOneInTheWild(modifier: Modifier = Modifier) {
            Box(
                modifier = modifier
                    .size(AvatarSize.Default.size)
                    .clip(CircleShape)
                    .then(colorModifier)
            ) {
                Box(
                    modifier = modifier.padding(spacesBorderWidth)
                )
            }
        }
      """
        .trimIndent()

    lint()
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/test.kt:3: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Row(modifier) {
              ^
          src/test.kt:4: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  SomethingElse(modifier)
                  ~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:9: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
              ^
          src/test.kt:11: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  SomethingDifferent(modifier = modifier)
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:16: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
              ^
          src/test.kt:19: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              SomethingElse(modifier = modifier)
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:20: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              SomethingElse(modifier = modifier.padding12())
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:25: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  SomethingElse(myMod)
                  ~~~~~~~~~~~~~~~~~~~~
          src/test.kt:26: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  SomethingElse(myMod)
                  ~~~~~~~~~~~~~~~~~~~~
          src/test.kt:31: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Box(
              ^
          src/test.kt:37: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  Box(
                  ^
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
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier = modifier) {
                ChildThatReusesModifier(modifier = modifier.fillMaxWidth())
            }
        }
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier = modifier) {
                val newModifier = modifier.fillMaxWidth()
                ChildThatReusesModifier(modifier = newModifier)
            }
        }
        @Composable
        fun Something(modifier: Modifier) {
            val newModifier = modifier.fillMaxWidth()
            Column(modifier = modifier) {
                ChildThatReusesModifier(modifier = newModifier)
            }
        }
      """
        .trimIndent()

    lint()
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/test.kt:3: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
              ^
          src/test.kt:4: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  ChildThatReusesModifier(modifier = modifier.fillMaxWidth())
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:9: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
              ^
          src/test.kt:11: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  ChildThatReusesModifier(modifier = newModifier)
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:17: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
              ^
          src/test.kt:18: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  ChildThatReusesModifier(modifier = newModifier)
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          6 errors, 0 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors when multiple Composables use the modifier even when it's been assigned to a new val`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun Something(modifier: Modifier) {
            val tweakedModifier = Modifier.then(modifier).fillMaxWidth()
            val reassignedModifier = modifier
            val modifier3 = Modifier.fillMaxWidth()
            Column(modifier = modifier) {
                OkComposable(modifier = newModifier)
                ComposableThaReusesModifier(modifier = tweakedModifier)
                ComposableThaReusesModifier(modifier = reassignedModifier)
                OkComposable(modifier = modifier3)
            }
            InnerComposable(modifier = tweakedModifier)
        }
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier = modifier) {
                val tweakedModifier = Modifier.then(modifier).fillMaxWidth()
                val reassignedModifier = modifier
                OkComposable(modifier = newModifier)
                ComposableThaReusesModifier(modifier = tweakedModifier)
                ComposableThaReusesModifier(modifier = reassignedModifier)
            }
        }
      """
        .trimIndent()

    lint()
      .files(kotlin(code))
      .allowCompilationErrors()
      .run()
      .expect(
        """
          src/test.kt:6: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
              ^
          src/test.kt:8: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  ComposableThaReusesModifier(modifier = tweakedModifier)
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:9: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  ComposableThaReusesModifier(modifier = reassignedModifier)
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:12: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              InnerComposable(modifier = tweakedModifier)
              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:16: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
              Column(modifier = modifier) {
              ^
          src/test.kt:20: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  ComposableThaReusesModifier(modifier = tweakedModifier)
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test.kt:21: Error: Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. modifier.fillMaxWidth().
          Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables.
          See https://twitter.github.io/compose-rules/rules/#dont-re-use-modifiers for more information. [ComposeModifierReused]
                  ComposableThaReusesModifier(modifier = reassignedModifier)
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
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
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier) {
                InternalComposable()
                Text("Hi")
            }
        }
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier) {
                ComposableWithNewModifier(Modifier.fillMaxWidth())
                Text("Hi", modifier = Modifier.padding12())
            }
        }
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier) {
                val newModifier = Modifier.weight(1f)
                ComposableWithNewModifier(newModifier)
                Text("Hi")
            }
        }
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier) {
                val newModifier = Modifier.weight(1f)
                if(shouldShowSomething) {
                    ComposableWithNewModifier(newModifier)
                } else {
                    DifferentComposableWithNewModifier(newModifier)
                }
            }
        }
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `passes when modifiers are reused for mutually exclusive branches`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun Something(modifier: Modifier = Modifier) {
            if (someCondition) {
                Case1RootLevelComposable(modifier = modifier.background(HorizonColor.Black))
            } else {
                Case2RootLevelComposable(modifier)
            }
        }
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }

  @Test
  fun `passes when used on vals with lambdas`() {
    @Language("kotlin")
    val code =
      """
        @Composable
        fun Something(modifier: Modifier) {
            Column(modifier = modifier) {
                TrustedFriendsMembersAppBar(
                    onBackClicked = { viewModel.processUserIntent(BackClicked) },
                    onDoneClicked = { viewModel.processUserIntent(DoneClicked) }
                )

                val recommendedEmptyUsersContent: @Composable ((Modifier) -> Unit)? = when {
                    !recommended.isEmpty -> null
                    searchQuery.value.isEmpty() -> { localModifier: Modifier ->
                        EmptyUsersList(
                            title = stringResource(trustedR.string.trusted_friends_members_list_empty_title),
                            description = stringResource(trustedR.string.trusted_friends_members_list_empty_description),
                            modifier = localModifier
                        )
                    }
                    else -> { localModifier ->
                        EmptyUsersList(
                            title = stringResource(trustedR.string.trusted_friends_search_empty_title),
                            description = stringResource(
                                trustedR.string.trusted_friends_search_empty_description,
                                searchQuery.value
                            ),
                            modifier = localModifier
                        )
                    }
                }
            }
        }
      """
        .trimIndent()
    lint().files(kotlin(code)).allowCompilationErrors().run().expectClean()
  }
}
