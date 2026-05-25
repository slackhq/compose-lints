// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UMethod
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.findAllParameterReferences
import slack.lint.compose.util.modifierParameter
import slack.lint.compose.util.sourceImplementation

/**
 * Reports the `modifier` parameter being read by more than one composable call on the same
 * execution path.
 *
 * The control-flow heavy lifting lives in [CodeFlowAnalysis.kt][buildCodeFlowGraph]: it turns the
 * method's UAST into a simplified AST, builds an acyclic [CodeFlowGraph], and finds calls that
 * reuse the modifier on a single path (see that file for the full algorithm and an annotated
 * example). This detector only wires that analysis into lint and reports the offending modifier
 * arguments.
 *
 * See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers
 */
class ModifierReusedDetector
@JvmOverloads
constructor(
  contentEmitterOption: ContentEmitterLintOption = ContentEmitterLintOption(CONTENT_EMITTER_OPTION)
) : ComposableFunctionDetector(contentEmitterOption to ISSUE), SourceCodeScanner {

  companion object {

    @VisibleForTesting var testCodeGraph = false
    @VisibleForTesting lateinit var codeFlowGraph: Map<GraphNode, Set<GraphNode>>

    val CONTENT_EMITTER_OPTION = ContentEmitterLintOption.newOption()

    val ISSUE =
      Issue.create(
          id = "ComposeModifierReused",
          briefDescription = "Modifiers should only be used once",
          explanation =
            """
              Modifiers should only be used once and by the root level layout of a Composable. This is true even if appended to or with other modifiers e.g. `modifier.fillMaxWidth()`. \
              Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to other composables. \
              See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information.
            """,
          category = Category.PRODUCTIVITY,
          priority = Priorities.NORMAL,
          severity = Severity.ERROR,
          implementation = sourceImplementation<ModifierReusedDetector>(),
        )
        .setOptions(listOf(CONTENT_EMITTER_OPTION))
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    val modifier = method.modifierParameter(context.evaluator) ?: return
    val modifierReferences = findAllParameterReferences(modifier, method)
    val graph = method.buildCodeFlowGraph(modifierReferences, computeNodeIds = testCodeGraph)
    if (testCodeGraph) codeFlowGraph = graph.adjacency

    graph
      .findCallsWithMultipleModifierUses()
      .flatMap { callExpression ->
        buildSet {
          callExpression.valueArgumentList?.visitReferencesSkipLambdas { reference ->
            if (reference in modifierReferences) add(reference)
          }
        }
      }
      .distinct()
      .forEach { modifierArgument ->
        context.report(
          ISSUE,
          modifierArgument,
          context.getLocation(modifierArgument),
          ISSUE.getExplanation(TextFormat.TEXT),
        )
      }
  }
}
