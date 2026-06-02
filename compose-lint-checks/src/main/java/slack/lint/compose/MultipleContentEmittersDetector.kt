// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.kotlin.isKotlin
import org.jetbrains.uast.toUElementOfType
import slack.lint.compose.util.OptionLoadingDetector
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.emitsContent
import slack.lint.compose.util.findChildrenByClass
import slack.lint.compose.util.hasReceiverType
import slack.lint.compose.util.isComposable
import slack.lint.compose.util.sourceImplementation
import slack.lint.compose.util.unwrapParenthesis

class MultipleContentEmittersDetector
@JvmOverloads
constructor(
  private val contentEmitterOption: ContentEmitterLintOption =
    ContentEmitterLintOption(CONTENT_EMITTER_OPTION)
) : OptionLoadingDetector(contentEmitterOption to ISSUE), SourceCodeScanner {

  companion object {

    val CONTENT_EMITTER_OPTION = ContentEmitterLintOption.newOption()

    private val ContextParameterNameRegex =
      Regex("""(?:\bcontext\s*\(|,)\s*([A-Za-z_][A-Za-z0-9_]*|_)\s*:""")

    val ISSUE =
      Issue.create(
          id = "ComposeMultipleContentEmitters",
          briefDescription = "Composables should only be emit from one source",
          explanation =
            """
              Composable functions should only be emitting content into the composition from one source at their top level.

              See https://slackhq.github.io/compose-lints/rules/#do-not-emit-multiple-pieces-of-content for more information.
            """,
          category = Category.PRODUCTIVITY,
          priority = Priorities.NORMAL,
          severity = Severity.ERROR,
          implementation = sourceImplementation<MultipleContentEmittersDetector>(),
        )
        .setOptions(listOf(CONTENT_EMITTER_OPTION))
  }

  internal val KtFunction.directUiEmitterCount: Int
    get() {
      return bodyBlockExpression?.let { block ->
        val directUiEmitterCalls = block.directUiEmitterCalls
        val directUiEmitterCount =
          if (directUiEmitterCalls.areAllScopedToContextParameters(contextParameterNames)) {
            0
          } else {
            directUiEmitterCalls.size
          }
        // If there's content emitted in a for loop, we assume there's at
        // least two iterations and thus count any emitters in them as multiple
        val forLoopCount =
          if (block.forLoopHasUiEmitters) {
            2
          } else {
            0
          }
        directUiEmitterCount + forLoopCount
      } ?: 0
    }

  internal val KtBlockExpression.forLoopHasUiEmitters: Boolean
    get() {
      return statements.filterIsInstance<KtForExpression>().any {
        when (val body = it.body) {
          is KtBlockExpression -> {
            body.directUiEmitterCount > 0
          }
          is KtCallExpression -> {
            body.emitsContent(contentEmitterOption.value)
          }
          else -> false
        }
      }
    }

  internal val KtBlockExpression.directUiEmitterCount: Int
    get() = directUiEmitterCalls.size

  internal fun KtFunction.indirectUiEmitterCount(mapping: Map<KtFunction, Int>): Int {
    val bodyBlock = bodyBlockExpression ?: return 0
    val contextParameterNames = contextParameterNames
    val indirectUiEmitterCalls =
      bodyBlock.statements
        .mapNotNull { it.unwrapParenthesis() }
        .filterIsInstance<KtCallExpression>()
        .filter { callExpression ->
          // If it's a direct hit on our list, it should count directly
          if (callExpression.emitsContent(contentEmitterOption.value)) return@filter true

          val name = callExpression.calleeExpression?.text ?: return@filter false
          // If the hit is in the provided mapping, it means it is using a composable that we know
          // emits UI, that we inferred from previous passes
          val value =
            mapping
              .mapKeys { entry -> entry.key.name }
              .getOrElse(name) {
                return@filter false
              }
          value > 0
        }
    return if (indirectUiEmitterCalls.areAllScopedToContextParameters(contextParameterNames)) {
      0
    } else {
      indirectUiEmitterCalls.size
    }
  }

  override fun getApplicableUastTypes() = listOf(UFile::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    if (!isKotlin(context.uastFile?.lang)) return null
    return object : UElementHandler() {
      override fun visitFile(node: UFile) {
        val file = node.sourcePsi as? KtFile ?: return
        // CHECK #1 : We want to find the composables first that are at risk of emitting content
        // from multiple sources.
        val composables =
          file
            .findChildrenByClass<KtFunction>()
            .filter { it.toUElementOfType<UMethod>()?.isComposable == true }
            // We don't want to analyze composables that are extension functions, as they might be
            // things like
            // BoxScope which are legit, and we want to avoid false positives.
            .filter { it.hasBlockBody() }
            // We want only methods with a body
            .filterNot { it.hasReceiverType }

        // Now we want to get the count of direct emitters in them: the composables we know for a
        // fact that output UI
        val composableToEmissionCount = composables.associateWith { it.directUiEmitterCount }

        // We can start showing errors, for composables that emit more than once (from the list of
        // known composables)
        val directEmissionsReported = composableToEmissionCount.filterValues { it > 1 }.keys
        for (composable in directEmissionsReported) {
          context.report(
            ISSUE,
            composable,
            context.getLocation(composable),
            ISSUE.getExplanation(TextFormat.TEXT),
          )
        }

        // Now we can give some extra passes through the list of composables, and try to get a more
        // accurate count.
        // We want to make sure that if these composables are using other composables in this file
        // that emit UI,
        // those are taken into account too. For example:
        // @Composable fun Comp1() { Text("Hi") }
        // @Composable fun Comp2() { Text("Hola") }
        // @Composable fun Comp3() { Comp1() Comp2() } // This wouldn't be picked up at first, but
        // should after 1 loop
        var currentMapping = composableToEmissionCount

        var shouldMakeAnotherPass = true
        while (shouldMakeAnotherPass) {
          val updatedMapping = currentMapping.mapValues { (functionNode, _) ->
            functionNode.indirectUiEmitterCount(currentMapping)
          }
          when {
            updatedMapping != currentMapping -> currentMapping = updatedMapping
            else -> shouldMakeAnotherPass = false
          }
        }

        // Here we have the settled data after all the needed passes, so we want to show errors for
        // them,
        // if they were not caught already by the 1st emission loop
        currentMapping
          .filterValues { it > 1 }
          .filterNot { directEmissionsReported.contains(it.key) }
          .keys
          .forEach { composable ->
            context.report(
              ISSUE,
              composable,
              context.getLocation(composable),
              ISSUE.getExplanation(TextFormat.TEXT),
            )
          }
      }
    }
  }

  private val KtFunction.contextParameterNames: Set<String>
    get() {
      return ContextParameterNameRegex.findAll(text.substringBefore("{")).mapNotNullTo(
        mutableSetOf()
      ) { match ->
        match.groupValues[1].takeUnless { name -> name == "_" }
      }
    }

  private val KtBlockExpression.directUiEmitterCalls: List<KtCallExpression>
    get() {
      return statements
        .mapNotNull { it.unwrapParenthesis() }
        .filterIsInstance<KtCallExpression>()
        .filter { it.emitsContent(contentEmitterOption.value) }
    }

  private fun List<KtCallExpression>.areAllScopedToContextParameters(
    contextParameterNames: Set<String>
  ): Boolean {
    return size > 1 &&
      contextParameterNames.isNotEmpty() &&
      all { it.referencesAnyContextParameter(contextParameterNames) }
  }

  private fun KtCallExpression.referencesAnyContextParameter(
    contextParameterNames: Set<String>
  ): Boolean {
    return findChildrenByClass<KtSimpleNameExpression>().any {
      it.getReferencedName() in contextParameterNames
    } || contextParameterNames.any { text.containsIdentifier(it) }
  }

  private fun String.containsIdentifier(name: String): Boolean {
    return windowed(name.length, partialWindows = false).withIndex().any { (index, value) ->
      value == name &&
        getOrNull(index - 1)?.isIdentifierPart() != true &&
        getOrNull(index + name.length)?.isIdentifierPart() != true
    }
  }

  private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_' || this == '$'
}
