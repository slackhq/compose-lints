// Copyright (C) 2024 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.ControlFlowGraph
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.asCall
import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.contracts.description.EventOccurrencesRange
import org.jetbrains.kotlin.contracts.description.canBeRevisited
import org.jetbrains.kotlin.contracts.description.canBeVisited
import org.jetbrains.kotlin.contracts.description.isDefinitelyVisited
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import slack.lint.compose.util.COMPOSABLE_FQ_NAME
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.callsInPlaceRange
import slack.lint.compose.util.findChildrenByClass
import slack.lint.compose.util.functionParameterCallTarget
import slack.lint.compose.util.ownerCall
import slack.lint.compose.util.slotParameters
import slack.lint.compose.util.sourceImplementation

class SlotReusedDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {

    val ISSUE =
      Issue.create(
        id = "SlotReused",
        briefDescription = "Slots should not be reused",
        explanation =
          issueText(
            """
            Slots should be invoked at most once on each execution path to meet lifecycle
            expectations. For example, calling a slot inside a branch and again afterward can
            compose two copies of the slot's content and internal state. Calling it once in each
            mutually exclusive branch is allowed because only one branch runs.

            See https://slackhq.github.io/compose-lints/rules/#do-not-invoke-slots-in-more-than-once-place for more information.
            """
          ),
        category = Category.CORRECTNESS,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<SlotReusedDetector>(),
      )
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    method.slotParameters().forEach { slotParameter ->
      val slot = slotParameter.sourcePsi as? KtParameter ?: return@forEach
      if (method.reusesSlot(context, function, slot)) {
        val location = slotParameter as UElement
        context.report(
          ISSUE,
          location,
          context.getLocation(location),
          ISSUE.getExplanation(TextFormat.TEXT),
        )
      }
    }
  }
}

/** Returns true when an execution path can invoke [slot] more than once. */
private fun UMethod.reusesSlot(
  context: JavaContext,
  enclosingFunction: KtFunction,
  slot: KtParameter,
): Boolean {
  val bodyExpression = enclosingFunction.bodyExpression ?: return false

  // Give each relevant call the number of slot uses it contributes to a path.
  val usesByCall = buildMap {
    bodyExpression.findChildrenByClass<KtCallExpression>().forEach { callExpression ->
      if (!callExpression.isExecutedBy(enclosingFunction, context)) return@forEach

      val useCount =
        callExpression.directInvocationCount(slot) + callExpression.passedAsSlotCount(context, slot)
      if (useCount > 0) {
        put(callExpression, useCount)
      }
    }
  }

  return hasExecutionPathWithMultipleUses(context, usesByCall)
}

/** Returns 1 when this call directly invokes [slot], or 0 otherwise. */
private fun KtCallExpression.directInvocationCount(slot: KtParameter): Int {
  val target = functionParameterCallTarget() ?: return 0
  return if (PsiEquivalenceUtil.areElementsEquivalent(target, slot)) 1 else 0
}

/** Returns true when the control-flow graph has a path with two or more slot uses. */
private fun UMethod.hasExecutionPathWithMultipleUses(
  context: JavaContext,
  usesByCall: Map<KtCallExpression, Int>,
): Boolean {
  if (usesByCall.isEmpty()) return false

  // Include lambda parameter calls so inline and composable lambda bodies appear in the graph.
  // Lint currently skips lambda arguments wrapped in parentheses.
  // https://issuetracker.google.com/issues/557281742
  val graph =
    ControlFlowGraph.create(
      this,
      ControlFlowGraph.Companion.Builder(
        strict = true,
        trackCallThrows = false,
        callLambdaParameters = true,
      ),
    )
  val body = uastBody ?: return false
  val entry = graph.getNode(body) ?: return false

  // Move the source-level use counts onto their matching control-flow nodes.
  val allNodes = graph.getAllNodes()
  val usesByNode = allNodes.associateWith { node ->
    val call = node.instruction as? UCallExpression
    val source = call?.sourcePsi as? KtCallExpression
    usesByCall[source] ?: 0
  }

  /** A graph position and the number of slot uses seen on the path to it. */
  data class State(val node: ControlFlowGraph.Node<UElement>, val useCount: Int)

  /** A literal lambda that can run during its owner call. */
  data class ImmediateLambda(
    val node: ControlFlowGraph.Node<UElement>,
    /** Whether the owner call always runs this lambda. */
    val mustExecute: Boolean,
    /** Whether a completing invocation can be followed by another invocation. */
    val canExecuteMoreThanOnce: Boolean,
  )

  /** The largest slot-use counts for the two ways a lambda can exit. */
  data class UseOutcomes(
    /** The largest count on a path that finishes the lambda, or null if no path does. */
    val completingMaximum: Int?,
    /** The largest count on a path that returns from the enclosing function, if any. */
    val nonLocalReturnMaximum: Int?,
  )

  /** Returns outgoing edges that enter literal lambda arguments of this call. */
  fun ControlFlowGraph.Node<UElement>.literalLambdaSuccessors() = successors.filter { edge ->
    val lambda = edge.to.instruction as? ULambdaExpression ?: return@filter false
    val callSource = instruction.sourcePsi ?: return@filter false
    val lambdaSource = lambda.sourcePsi ?: return@filter false
    PsiTreeUtil.isAncestor(callSource, lambdaSource, true)
  }

  val immediateLambdasByOwner =
    mutableMapOf<ControlFlowGraph.Node<UElement>, List<ImmediateLambda>>()

  /** Returns literal lambda arguments whose bodies can run during this call. */
  fun ControlFlowGraph.Node<UElement>.immediateLiteralLambdas(): List<ImmediateLambda> {
    return immediateLambdasByOwner.getOrPut(this) {
      val ownerCall = instruction.sourcePsi as? KtCallExpression ?: return@getOrPut emptyList()
      literalLambdaSuccessors().mapNotNull { edge ->
        val lambda = edge.to.instruction.sourcePsi as? KtLambdaExpression ?: return@mapNotNull null
        val range = lambda.callsInPlaceRange(ownerCall)
        when {
          // Zero invocations means the lambda body cannot contribute a use.
          range == EventOccurrencesRange.ZERO -> null

          // Contracts tell us both whether the lambda can run and whether it must run.
          range != null ->
            ImmediateLambda(
              edge.to,
              mustExecute = range.isDefinitelyVisited(),
              canExecuteMoreThanOnce = range.canBeRevisited(),
            )

          // A composable slot can run during composition, but the callee may choose not to call it.
          lambda.isComposableSlotArgument(context, ownerCall) ->
            ImmediateLambda(edge.to, mustExecute = false, canExecuteMoreThanOnce = false)
          else -> null
        }
      }
    }
  }

  /** Returns true when this element is inside [lambda]. */
  fun UElement.isInside(lambda: ULambdaExpression): Boolean {
    return generateSequence(this as UElement?) { it.uastParent }
      .any {
        it === lambda ||
          it is ULambdaExpression && it.sourcePsi != null && it.sourcePsi === lambda.sourcePsi
      }
  }

  /** Counts slot uses in lambda arguments that run during an owner call. */
  val lambdaAnalyzer =
    object {
      private val lambdaUseSummaries = mutableMapOf<ControlFlowGraph.Node<UElement>, UseOutcomes>()
      private val immediateOutcomesByOwner =
        mutableMapOf<ControlFlowGraph.Node<UElement>, UseOutcomes>()

      /** Combines the slot uses from literal lambdas owned by [owner]. */
      fun immediateUseOutcomes(owner: ControlFlowGraph.Node<UElement>): UseOutcomes {
        return immediateOutcomesByOwner.getOrPut(owner) {
          // Analyze each lambda on its own before adding its uses to the owner call.
          val summaries =
            owner.immediateLiteralLambdas().map { immediate ->
              val summary = lambdaUseSummary(immediate.node, owner)
              // Only a completing invocation can be followed by another invocation.
              val repeatedSummary =
                if (immediate.canExecuteMoreThanOnce && (summary.completingMaximum ?: 0) > 0) {
                  summary.copy(completingMaximum = 2)
                } else {
                  summary
                }
              immediate to repeatedSummary
            }
          if (summaries.isEmpty()) return@getOrPut UseOutcomes(0, null)

          // The owner can continue only when every lambda that must run has a completing path.
          val completingMaximum =
            if (
              summaries.all { (immediate, summary) ->
                !immediate.mustExecute || summary.completingMaximum != null
              }
            ) {
              summaries.sumOf { (_, summary) -> summary.completingMaximum ?: 0 }.coerceAtMost(2)
            } else {
              null
            }

          // A non-local return ends the owner path, so combine it with the other lambdas that
          // complete.
          val nonLocalReturnMaximum =
            summaries
              .mapIndexedNotNull { returningIndex, (_, returningSummary) ->
                val returningUses =
                  returningSummary.nonLocalReturnMaximum ?: return@mapIndexedNotNull null
                summaries
                  .mapIndexed { index, (_, summary) ->
                    if (index == returningIndex) returningUses else summary.completingMaximum ?: 0
                  }
                  .sum()
                  .coerceAtMost(2)
              }
              .maxOrNull()
          UseOutcomes(completingMaximum, nonLocalReturnMaximum)
        }
      }

      /** Counts slot uses on paths that finish this lambda or return from its caller. */
      private fun lambdaUseSummary(
        start: ControlFlowGraph.Node<UElement>,
        owner: ControlFlowGraph.Node<UElement>,
      ): UseOutcomes {
        lambdaUseSummaries[start]?.let {
          return it
        }
        val lambda = start.instruction as? ULambdaExpression ?: return UseOutcomes(0, null)
        val pending = ArrayDeque<State>()
        val visited = mutableSetOf<State>()
        var completingMaximum: Int? = null
        var nonLocalReturnMaximum: Int? = null

        /** Records the largest count for the kind of exit at [node]. */
        fun recordExit(node: ControlFlowGraph.Node<UElement>, useCount: Int) {
          if (node.instruction is UReturnExpression) {
            nonLocalReturnMaximum = maxOf(nonLocalReturnMaximum ?: 0, useCount)
          } else {
            completingMaximum = maxOf(completingMaximum ?: 0, useCount)
          }
        }

        pending.addLast(State(start, 0))
        while (pending.isNotEmpty()) {
          val state = pending.removeLast()
          if (!state.node.instruction.isInside(lambda) || !visited.add(state)) continue

          // Cap every count at two because larger counts do not change the result.
          val useCount = (state.useCount + usesByNode.getValue(state.node)).coerceAtMost(2)

          // Add calls from immediate nested lambdas without traversing their graph edges twice.
          val immediateOutcomes = immediateUseOutcomes(state.node)
          immediateOutcomes.nonLocalReturnMaximum?.let { nestedUses ->
            nonLocalReturnMaximum =
              maxOf(nonLocalReturnMaximum ?: 0, (useCount + nestedUses).coerceAtMost(2))
          }

          // A required nested lambda that cannot complete also prevents this path from continuing.
          val completingUses = immediateOutcomes.completingMaximum ?: continue
          val continuingUseCount = (useCount + completingUses).coerceAtMost(2)

          // Literal lambda bodies were summarized above, so follow only the owner's remaining
          // edges.
          val literalLambdas = state.node.literalLambdaSuccessors()
          val outgoing =
            state.node.successors.filterNot { it in literalLambdas } + state.node.exceptions
          if (outgoing.isEmpty()) {
            recordExit(state.node, continuingUseCount)
          }
          outgoing.forEach { edge ->
            when {
              // Returning to the owner means this lambda finished normally.
              edge.to === owner ->
                completingMaximum = maxOf(completingMaximum ?: 0, continuingUseCount)
              edge.to.instruction.isInside(lambda) ->
                pending.addLast(State(edge.to, continuingUseCount))
              // Any other edge leaves the lambda through a return or another terminal path.
              else -> recordExit(state.node, continuingUseCount)
            }
          }
        }

        return UseOutcomes(completingMaximum, nonLocalReturnMaximum).also {
          lambdaUseSummaries[start] = it
        }
      }
    }

  /** Returns true when a path from [start] reaches two slot uses. */
  fun hasPathWithMultipleUses(start: ControlFlowGraph.Node<UElement>): Boolean {
    val pending = ArrayDeque<State>()
    val visited = mutableSetOf<State>()

    pending.addLast(State(start, 0))
    while (pending.isNotEmpty()) {
      val state = pending.removeLast()
      if (!visited.add(state)) continue

      val useCount = (state.useCount + usesByNode.getValue(state.node)).coerceAtMost(2)

      // Fold immediate lambda bodies into their owner call before continuing through the method.
      val immediateOutcomes = lambdaAnalyzer.immediateUseOutcomes(state.node)
      if (immediateOutcomes.nonLocalReturnMaximum?.let { useCount + it >= 2 } == true) {
        return true
      }

      // A required lambda with no completing path stops this method path at the owner call.
      val completingUses = immediateOutcomes.completingMaximum ?: continue
      val continuingUseCount = (useCount + completingUses).coerceAtMost(2)
      if (continuingUseCount == 2) return true

      // Lambda successors were already summarized, so walk the rest of the method graph.
      val literalLambdas = state.node.literalLambdaSuccessors()
      state.node.successors
        .filterNot { it in literalLambdas }
        .forEach { pending.addLast(State(it.to, continuingUseCount)) }
      state.node.exceptions.forEach { pending.addLast(State(it.to, continuingUseCount)) }
    }
    return false
  }
  return hasPathWithMultipleUses(entry)
}

/** Returns true when this call runs while [enclosingFunction] runs. */
private fun KtCallExpression.isExecutedBy(
  enclosingFunction: KtFunction,
  context: JavaContext,
): Boolean {
  var ancestor: PsiElement? = parent
  while (ancestor != null) {
    when (ancestor) {
      is KtNamedFunction -> return ancestor === enclosingFunction
      is KtLambdaExpression -> {
        val ownerCall = ancestor.ownerCall()
        // Calls inside deferred lambdas do not run as part of the enclosing composable call.
        if (
          ownerCall != null &&
            !ancestor.isComposableSlotArgument(context, ownerCall) &&
            ancestor.callsInPlaceRange(ownerCall)?.canBeVisited() != true
        ) {
          return false
        }
      }
    }
    ancestor = ancestor.parent
  }
  return false
}

/** Returns true when this lambda is passed to a composable slot parameter. */
private fun KtLambdaExpression.isComposableSlotArgument(
  context: JavaContext,
  ownerCall: KtCallExpression,
): Boolean {
  val uCall = ownerCall.toUElement()?.asCall() ?: return false
  val method = uCall.resolve() ?: return false
  if (
    !method.hasAnnotation(COMPOSABLE_FQ_NAME) ||
      method.returnType?.isAssignableFrom(PsiTypes.voidType()) != true
  ) {
    return false
  }
  return context.evaluator.computeArgumentMapping(uCall, method).any { (argument, parameter) ->
    parameter.type.hasAnnotation(COMPOSABLE_FQ_NAME) &&
      argument.sourcePsi?.let { it === this || PsiTreeUtil.isAncestor(it, this, false) } == true
  }
}

/** Counts how many composable slot arguments receive [slot] in this call. */
private fun KtCallExpression.passedAsSlotCount(
  context: JavaContext,
  slot: KtParameter,
): Int {
  val uCallExpression = toUElement()?.asCall() ?: return 0
  val psiMethod = uCallExpression.resolve() ?: return 0
  if (
    !psiMethod.hasAnnotation(COMPOSABLE_FQ_NAME) ||
      psiMethod.returnType?.isAssignableFrom(PsiTypes.voidType()) != true
  ) {
    return 0
  }

  // Treat a composable Unit call as one use of each slot argument it receives.
  return context.evaluator.computeArgumentMapping(uCallExpression, psiMethod).count {
    (expression, parameter) ->
    val argumentElement: PsiElement = expression.tryResolve() ?: return@count false
    parameter.type.hasAnnotation(COMPOSABLE_FQ_NAME) &&
      (argumentElement.isEquivalentTo(slot) ||
        PsiEquivalenceUtil.areElementsEquivalent(argumentElement, slot))
  }
}
