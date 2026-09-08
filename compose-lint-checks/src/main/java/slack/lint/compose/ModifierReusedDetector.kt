// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.uast.UMethod
import slack.lint.compose.util.ComposableCallKind
import slack.lint.compose.util.ModifierQualifiedNames
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.callsNonReadOnlyComposableFunctionParameter
import slack.lint.compose.util.composableCallKind
import slack.lint.compose.util.directCall
import slack.lint.compose.util.directCallIncludingSafe
import slack.lint.compose.util.expandedClassId
import slack.lint.compose.util.findAllParameterReferences
import slack.lint.compose.util.findReferencesTo
import slack.lint.compose.util.functionParameterCallTarget
import slack.lint.compose.util.isModifier
import slack.lint.compose.util.isNonReadOnlyComposableCall
import slack.lint.compose.util.isNonReadOnlyComposableFunctionType
import slack.lint.compose.util.isNonReadOnlyUnitComposableCall
import slack.lint.compose.util.isPossiblyExecutedBy
import slack.lint.compose.util.ownerCall
import slack.lint.compose.util.resolvesToAnyEquivalent
import slack.lint.compose.util.returnsNothing
import slack.lint.compose.util.returnsTo
import slack.lint.compose.util.returnsUnitOrVoid
import slack.lint.compose.util.slotParameters
import slack.lint.compose.util.sourceImplementation
import slack.lint.compose.util.unwrapParenthesis
import slack.lint.compose.util.unwrapReturnExpression

/**
 * Reports modifier reuse and top-level branches that omit the modifier.
 *
 * The modifier reuse check uses [buildCodeFlowGraph]. See its documentation for the algorithm and
 * an annotated example.
 *
 * See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers
 */
class ModifierReusedDetector
@JvmOverloads
constructor(
  contentEmitterOption: ContentEmitterLintOption = ContentEmitterLintOption(CONTENT_EMITTER_OPTION)
) : ComposableFunctionDetector(contentEmitterOption to ISSUE), SourceCodeScanner {

  companion object {
    private val MODIFIER_CLASS_IDS =
      ModifierQualifiedNames.mapTo(mutableSetOf()) { ClassId.topLevel(FqName(it)) }

    private val REUSED_MESSAGE =
      issueText(
        """
        The modifier is passed to more than one composable on this execution path. Pass it only to
        the top-level layout, and use `Modifier` for child composables.

        See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information.
        """
      )

    private val NOT_FORWARDED_MESSAGE =
      issueText(
        """
        One branch passes the modifier parameter, but this branch does not. Pass it to every
        branch whose composable accepts a modifier.

        See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information.
        """
      )

    @VisibleForTesting var testCodeGraph = false
    @VisibleForTesting lateinit var codeFlowGraph: Map<GraphNode, Set<GraphNode>>

    val CONTENT_EMITTER_OPTION = ContentEmitterLintOption.newOption()

    val ISSUE =
      Issue.create(
          id = "ComposeModifierReused",
          briefDescription = "Use modifiers once across branches",
          explanation =
            issueText(
              """
              Modifiers should only be used once and by the root level layout of a Composable. This
              is true even if appended to or with other modifiers e.g. `modifier.fillMaxWidth()`.
              Use Modifier (with a capital 'M') to construct a new Modifier that you can pass to
              other composables.

              For a top-level `if` or `when`, pass the modifier parameter to every branch whose
              composable accepts one. Only one branch runs, so this does not reuse the modifier.

              See https://slackhq.github.io/compose-lints/rules/#dont-re-use-modifiers for more information.
              """
            ),
          category = Category.PRODUCTIVITY,
          priority = Priorities.NORMAL,
          severity = Severity.ERROR,
          implementation = sourceImplementation<ModifierReusedDetector>(),
        )
        .setOptions(listOf(CONTENT_EMITTER_OPTION))
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    val namedFunction = function as? KtNamedFunction ?: return
    val valueParameters = method.uastParameters.takeLast(namedFunction.valueParameters.size)
    val modifiers = valueParameters.filter { it.isModifier(context.evaluator) }
    val modifier =
      modifiers.firstOrNull { (it.sourcePsi as? KtParameter)?.name == "modifier" }
        ?: modifiers.firstOrNull()
        ?: return
    val modifierParameter = modifier.sourcePsi as? KtParameter ?: return
    val modifierClassId =
      modifierParameter.expandedClassId()?.takeIf { it in MODIFIER_CLASS_IDS }
        ?: ModifierQualifiedNames.firstOrNull {
            context.evaluator.typeMatches(modifier.type, it)
          }
          ?.let { ClassId.topLevel(FqName(it)) }

    val hasSyntheticParameters = method.uastParameters.size != namedFunction.valueParameters.size

    // UAST adds context and extension receivers to the parameter list. Use Kotlin PSI when those
    // extra parameters make the UAST indexes unreliable.
    val modifierReferences =
      if (hasSyntheticParameters) {
        if (modifierClassId != null) {
          namedFunction.findModifierAliasReferences(modifierParameter, modifierClassId)
        } else {
          namedFunction.findReferencesTo(setOf(modifierParameter))
        }
      } else {
        findAllParameterReferences(modifier, method)
      }

    val composableFunctionParameters =
      method.slotParameters().mapNotNullTo(mutableSetOf()) { it.sourcePsi }

    val graph =
      method.buildCodeFlowGraph(
        modifierReferences,
        composableFunctionParameters,
        computeNodeIds = testCodeGraph,
      )

    if (testCodeGraph) {
      codeFlowGraph = graph.adjacency
    }

    graph
      .findCallsOnPathsWithMultipleUses()
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
          REUSED_MESSAGE,
        )
      }

    modifierClassId?.let {
      reportMissingBranchModifiers(context, method, namedFunction, modifierParameter, it)
    }
  }

  /** Reports branch calls that omit the modifier when another branch forwards it. */
  private fun reportMissingBranchModifiers(
    context: JavaContext,
    method: UMethod,
    function: KtNamedFunction,
    modifierParameter: KtParameter,
    modifierClassId: ClassId,
  ) {
    if (!method.returnsUnitOrVoid(context.evaluator)) return
    function.findBranchesMissingModifier(modifierParameter, modifierClassId).forEach { branchCall ->
      val callSite = branchCall.calleeExpression ?: branchCall
      context.report(
        ISSUE,
        callSite,
        context.getLocation(callSite),
        NOT_FORWARDED_MESSAGE,
      )
    }
  }
}

/** Finds references to the modifier parameter and local values assigned from it. */
private fun KtNamedFunction.findModifierAliasReferences(
  parameter: KtParameter,
  modifierClassId: ClassId,
): Set<PsiElement> {
  return findReferencesTo(modifierAliases(parameter, modifierClassId, AliasKind.POSSIBLE))
}

/** Finds top-level branch calls that accept a modifier but do not receive this modifier. */
private fun KtNamedFunction.findBranchesMissingModifier(
  parameter: KtParameter,
  modifierClassId: ClassId,
): List<KtCallExpression> {
  val modifierDeclarations = modifierAliases(parameter, modifierClassId, AliasKind.DEFINITE)
  val conditional = rootConditionalExpression(modifierDeclarations) ?: return emptyList()
  val modifierSlots = valueParameters.mapNotNull { it.modifierSlot(modifierClassId) }
  val branchCalls =
    conditional.branchCalls(modifierDeclarations).mapNotNull {
      it.resolveBranchCall(modifierClassId, modifierSlots)
    }
  val branchForwarding = branchCalls.associateWith { it.forwarding(modifierDeclarations) }

  // A missing argument only matters when a sibling branch establishes that the modifier belongs
  // at this level.
  if (ModifierForwarding.YES !in branchForwarding.values) return emptyList()
  return branchForwarding.filterValues { it == ModifierForwarding.NO }.keys.map { it.call }
}

/** Controls whether an alias can sometimes contain the modifier or must always contain it. */
private enum class AliasKind {
  POSSIBLE,
  DEFINITE,
}

/** Collects immutable local values assigned from the modifier, including chains of local values. */
private fun KtNamedFunction.modifierAliases(
  parameter: KtParameter,
  modifierClassId: ClassId,
  kind: AliasKind,
): Set<PsiElement> {
  val declarations = mutableSetOf<PsiElement>(parameter)

  // Visit in source order so a local value can refer to an alias declared above it.
  accept(
    object : KtTreeVisitorVoid() {
      override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)

        if (
          property.isLocal() && !property.isVar && property.expandedClassId() == modifierClassId
        ) {
          val initializer = property.initializer

          // Reuse analysis needs values that may contain the modifier. Branch analysis needs
          // values that contain it on every path that completes.
          val isAlias =
            when (kind) {
              AliasKind.POSSIBLE -> initializer?.flowsFrom(declarations) == true
              AliasKind.DEFINITE -> initializer?.definitelyFlowsFrom(declarations) == true
            }

          if (isAlias) declarations += property
        }
      }
    }
  )
  return declarations
}

/** Returns true when this expression may produce a value from one of [declarations]. */
private fun KtExpression.flowsFrom(declarations: Set<PsiElement>): Boolean {
  return when (val expression = unwrapParenthesis()) {
    is KtNameReferenceExpression -> expression.resolvesToAnyEquivalent(declarations)
    is KtBlockExpression -> expression.statements.lastOrNull()?.flowsFrom(declarations) == true
    is KtReturnExpression -> expression.returnedExpression?.flowsFrom(declarations) == true
    is KtIfExpression ->
      expression.then?.flowsFrom(declarations) == true ||
        expression.`else`?.flowsFrom(declarations) == true
    is KtWhenExpression -> expression.entries.any { it.expression?.flowsFrom(declarations) == true }
    else -> {
      val directCall = expression?.directCall() ?: return false

      // Modifier chains and wrapper calls can carry the value through a receiver or eager
      // argument.
      directCall.receiver?.flowsFrom(declarations) == true ||
        directCall.call.valueArgumentList?.arguments.orEmpty().any { argument ->
          val argumentExpression = argument.getArgumentExpression()
          argumentExpression !is KtLambdaExpression &&
            argumentExpression?.flowsFrom(declarations) == true
        }
    }
  }
}

/** Returns true when every path that completes produces a value from [declarations]. */
private fun KtExpression.definitelyFlowsFrom(declarations: Set<PsiElement>): Boolean {
  return forwardingFrom(declarations) == ModifierForwarding.YES
}

/** Reports whether completed paths produce a value from [declarations]. */
private fun KtExpression.forwardingFrom(
  declarations: Set<PsiElement>,
  returningLambda: KtLambdaExpression? = null,
): ModifierForwarding {
  return when (val expression = unwrapParenthesis()) {
    is KtNameReferenceExpression ->
      if (expression.resolvesToAnyEquivalent(declarations)) {
        ModifierForwarding.YES
      } else {
        ModifierForwarding.NO
      }
    is KtBlockExpression ->
      expression.statements.lastOrNull()?.forwardingFrom(declarations, returningLambda)
        ?: ModifierForwarding.NO

    // A local run return produces the lambda's result. Other returns leave this execution path.
    is KtReturnExpression ->
      if (returningLambda != null && expression.returnsTo(returningLambda)) {
        expression.returnedExpression?.forwardingFrom(declarations, returningLambda)
          ?: ModifierForwarding.NO
      } else {
        ModifierForwarding.DOES_NOT_COMPLETE
      }
    is KtThrowExpression -> ModifierForwarding.DOES_NOT_COMPLETE
    is KtIfExpression ->
      listOf(
          expression.then?.forwardingFrom(declarations, returningLambda) ?: ModifierForwarding.NO,
          expression.`else`?.forwardingFrom(declarations, returningLambda) ?: ModifierForwarding.NO,
        )
        .combineForwarding()
    is KtWhenExpression ->
      expression.entries
        .map {
          it.expression?.forwardingFrom(declarations, returningLambda) ?: ModifierForwarding.NO
        }
        .combineForwarding()
    else -> {
      val directCall = expression?.directCall() ?: return ModifierForwarding.NO

      // A call returning Nothing cannot reach the branch call either.
      if (directCall.call.returnsNothing()) return ModifierForwarding.DOES_NOT_COMPLETE

      directCall.call.runResultForwarding(declarations)?.let {
        return it
      }

      // Do not treat a modifier captured inside a lambda as the call's result. Only the receiver
      // and non-lambda arguments count here.
      val components = buildList {
        directCall.receiver?.let { add(it.forwardingFrom(declarations, returningLambda)) }
        directCall.call.valueArgumentList?.arguments.orEmpty().forEach { argument ->
          val argumentExpression = argument.getArgumentExpression()
          if (argumentExpression != null && argumentExpression !is KtLambdaExpression) {
            add(argumentExpression.forwardingFrom(declarations, returningLambda))
          }
        }
      }

      // Every eager component must complete before the call can complete. If they do, one
      // forwarding component is enough for the result to count as forwarding.
      when {
        ModifierForwarding.DOES_NOT_COMPLETE in components -> ModifierForwarding.DOES_NOT_COMPLETE
        ModifierForwarding.YES in components -> ModifierForwarding.YES
        else -> ModifierForwarding.NO
      }
    }
  }
}

/** Returns the forwarding result of the standard library's non-extension `run` function. */
private fun KtCallExpression.runResultForwarding(
  declarations: Set<PsiElement>
): ModifierForwarding? {
  val lambda =
    analyze(this) {
      val call = resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze null
      if (
        call.symbol.callableId?.asSingleFqName()?.asString() != "kotlin.run" ||
          call.symbol.receiverParameter != null
      ) {
        return@analyze null
      }
      call.valueArgumentMapping.keys.singleOrNull()?.unwrapParenthesis() as? KtLambdaExpression
    } ?: return null
  val body = lambda.bodyExpression ?: return ModifierForwarding.NO
  val results = mutableListOf(body.forwardingFrom(declarations, lambda))

  // Early labeled returns are also results of run, even when the final expression forwards.
  body.accept(
    object : KtTreeVisitorVoid() {
      override fun visitReturnExpression(expression: KtReturnExpression) {
        if (expression.returnsTo(lambda)) {
          results += expression.forwardingFrom(declarations, lambda)
        }
        super.visitReturnExpression(expression)
      }
    }
  )
  return results.combineForwarding()
}

/** Combines forwarding results from alternate branches. */
private fun List<ModifierForwarding>.combineForwarding(): ModifierForwarding {
  val completing = filterNot { it == ModifierForwarding.DOES_NOT_COMPLETE }
  return when {
    completing.isEmpty() -> ModifierForwarding.DOES_NOT_COMPLETE
    completing.all { it == ModifierForwarding.YES } -> ModifierForwarding.YES
    else -> ModifierForwarding.NO
  }
}

/** Says whether an expression forwards the modifier, does not forward it, or never completes. */
private enum class ModifierForwarding {
  YES,
  NO,
  DOES_NOT_COMPLETE,
}

/** Returns the function's top-level `if` or `when` after any safe setup statements. */
private fun KtNamedFunction.rootConditionalExpression(
  modifierDeclarations: Set<PsiElement>
): KtExpression? {
  val body = bodyExpression?.unwrapParenthesis() ?: return null
  val candidate =
    if (body is KtBlockExpression) {
      body.lastExpressionAfterSetup(modifierDeclarations)
    } else {
      body
    }
  return candidate?.unwrapReturnExpression()?.unwrapParenthesis()?.takeIf {
    it is KtIfExpression || it is KtWhenExpression
  }
}

/** Returns the last expression when all earlier statements are safe setup. */
private fun KtBlockExpression.lastExpressionAfterSetup(
  modifierDeclarations: Set<PsiElement>
): KtExpression? {
  if (statements.dropLast(1).any { !it.isSafeSetup(modifierDeclarations) }) return null
  return statements.lastOrNull()
}

/** Returns true when this expression does not emit content or consume the modifier. */
private fun KtExpression.isSafeSetup(modifierDeclarations: Set<PsiElement>): Boolean {
  val setupExpression = if (this is KtProperty) initializer ?: return false else this
  var emitsContent = false

  setupExpression.accept(
    object : KtTreeVisitorVoid() {
      override fun visitNamedFunction(function: KtNamedFunction) {
        // Declaring a local function does not execute its body.
      }

      override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
        // Ignore deferred lambdas because their bodies do not run during setup.
        val ownerCall = lambdaExpression.ownerCall() ?: return
        if (lambdaExpression.isPossiblyExecutedBy(ownerCall)) {
          super.visitLambdaExpression(lambdaExpression)
        }
      }

      override fun visitCallExpression(expression: KtCallExpression) {
        val consumesModifier =
          expression.valueArguments.any { argument ->
            argument.getArgumentExpression()?.definitelyFlowsFrom(modifierDeclarations) == true
          }

        // Unit composables and slot calls emit content without using the modifier. Other
        // composable calls matter here only when they consume it.
        if (
          expression.isNonReadOnlyUnitComposableCall() ||
            expression.callsNonReadOnlyComposableFunctionParameter() ||
            expression.isNonReadOnlyComposableCall() && consumesModifier
        ) {
          emitsContent = true
        } else {
          super.visitCallExpression(expression)
        }
      }
    }
  )

  return !emitsContent
}

/** Collects the final call from each branch of nested `if` and `when` expressions. */
private fun KtExpression.branchCalls(
  modifierDeclarations: Set<PsiElement>
): List<KtCallExpression> {
  return when (val expression = unwrapParenthesis()) {
    is KtBlockExpression ->
      expression
        .lastExpressionAfterSetup(modifierDeclarations)
        ?.branchCalls(modifierDeclarations)
        .orEmpty()
    is KtReturnExpression ->
      expression.returnedExpression?.branchCalls(modifierDeclarations).orEmpty()
    is KtIfExpression ->
      buildList {
        expression.then?.let { addAll(it.branchCalls(modifierDeclarations)) }
        expression.`else`?.let { addAll(it.branchCalls(modifierDeclarations)) }
      }
    is KtWhenExpression ->
      expression.entries.flatMap { entry ->
        entry.expression?.branchCalls(modifierDeclarations).orEmpty()
      }
    else -> expression?.directCallIncludingSafe()?.call?.let(::listOf).orEmpty()
  }
}

/** Finds the modifier position when this is a composable function parameter returning `Unit`. */
private fun KtParameter.modifierSlot(modifierClassId: ClassId): ModifierSlot? {
  return analyze(this) {
    val functionType = symbol.returnType.fullyExpandedType as? KaFunctionType ?: return@analyze null
    val returnType = functionType.returnType.fullyExpandedType
    if (
      !functionType.isNonReadOnlyComposableFunctionType() ||
        returnType.isMarkedNullable ||
        (returnType as? KaClassType)?.classId != StandardClassIds.Unit
    ) {
      return@analyze null
    }

    // Zero or multiple modifier parameters would make positional matching ambiguous.
    val modifierParameterIndex =
      functionType.parameterTypes
        .withIndex()
        .singleOrNull { (_, type) ->
          (type.fullyExpandedType as? KaClassType)?.classId == modifierClassId
        }
        ?.index ?: return@analyze null
    ModifierSlot(
      parameter = this@modifierSlot,
      modifierParameterIndex = modifierParameterIndex,
      valueParameterCount = functionType.parameterTypes.size,
      hasExtensionReceiver = functionType.receiverType != null,
    )
  }
}

/** Resolves a branch call and the argument mapped to its modifier parameter. */
private fun KtCallExpression.resolveBranchCall(
  modifierClassId: ClassId,
  modifierSlots: List<ModifierSlot>,
): BranchCall? {
  val calleeTarget = functionParameterCallTarget()
  val modifierSlot = modifierSlots.firstOrNull {
    calleeTarget != null && PsiEquivalenceUtil.areElementsEquivalent(it.parameter, calleeTarget)
  }
  if (modifierSlot != null) {
    // Prefer resolved argument mapping because it handles the different ways a function value can
    // be invoked without guessing positions.
    val mappedArgument =
      analyze(this) {
        val functionCall = resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze null
        functionCall.valueArgumentMapping.entries
          .singleOrNull { (_, parameter) ->
            (parameter.returnType.fullyExpandedType as? KaClassType)?.classId == modifierClassId
          }
          ?.key
      }
    if (mappedArgument != null) return BranchCall(this, mappedArgument)

    // Some function-value calls resolve without an argument mapping. Fall back to positions only
    // when every argument is unnamed and the argument count matches the function type.
    val explicitArguments = valueArgumentList?.arguments.orEmpty() + lambdaArguments
    if (explicitArguments.any { it.getArgumentName() != null }) return null

    // An explicitly passed extension receiver comes before the function type's value parameters.
    val argumentOffset =
      when (explicitArguments.size) {
        modifierSlot.valueParameterCount -> 0
        modifierSlot.valueParameterCount + 1 ->
          if (modifierSlot.hasExtensionReceiver) 1 else return null
        else -> return null
      }
    val argument =
      explicitArguments
        .getOrNull(modifierSlot.modifierParameterIndex + argumentOffset)
        ?.getArgumentExpression()
    return BranchCall(this, argument)
  }

  // Otherwise this must be an ordinary non-read-only composable that returns Unit.
  return analyze(this) {
    val functionCall = resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze null
    val returnType = functionCall.symbol.returnType.fullyExpandedType
    if (
      composableCallKind { it in functionCall.symbol.annotations } !=
        ComposableCallKind.NON_READ_ONLY ||
        returnType.isMarkedNullable ||
        (returnType as? KaClassType)?.classId != StandardClassIds.Unit
    ) {
      return@analyze null
    }
    val modifierParameters =
      functionCall.signature.valueParameters.filter { parameter ->
        (parameter.returnType.fullyExpandedType as? KaClassType)?.classId == modifierClassId
      }

    // Prefer the conventional name. Without it, accept the call only when there is one choice.
    val modifierParameter =
      modifierParameters.firstOrNull { it.name.asString() == "modifier" }
        ?: modifierParameters.singleOrNull()
        ?: return@analyze null

    val argument =
      functionCall.valueArgumentMapping.entries
        .firstOrNull { (_, parameter) -> parameter.symbol == modifierParameter.symbol }
        ?.key
    BranchCall(this@resolveBranchCall, argument)
  }
}

/** Describes the modifier position in a composable function parameter. */
private data class ModifierSlot(
  val parameter: KtParameter,
  val modifierParameterIndex: Int,
  val valueParameterCount: Int,
  val hasExtensionReceiver: Boolean,
)

/** Pairs a branch call with its modifier argument, or null when no argument was supplied. */
private data class BranchCall(
  val call: KtCallExpression,
  val modifierArgument: PsiElement?,
)

/** Classifies whether this call's modifier argument forwards [modifierDeclarations]. */
private fun BranchCall.forwarding(modifierDeclarations: Set<PsiElement>): ModifierForwarding {
  return (modifierArgument as? KtExpression)?.forwardingFrom(modifierDeclarations)
    ?: ModifierForwarding.NO
}
