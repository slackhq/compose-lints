// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.checks.DataFlowAnalyzer
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.kotlin.KotlinLocalFunctionULambdaExpression
import org.jetbrains.uast.kotlin.KotlinUReturnExpression
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.isComposable
import slack.lint.compose.util.modifierParameter
import slack.lint.compose.util.sourceImplementation

/**
 * This lint detector performs **code flow analysis** to identify execution paths within a method
 * that read the `modifier` parameter multiple times.
 *
 * ---
 *
 * ## Overview
 *
 * ### 1. Converting UAST to a simplified AST
 *
 * The **UAST** provides a detailed representation of code, including annotations, identifiers,
 * references, and expressions. However, for this analysis, most of these details are irrelevant. To
 * simplify the analysis, we convert **UAST** into a **simplified AST**, which retains only the
 * nodes needed for code flow analysis:
 * - **Composable function calls** that use the `modifier` parameter
 * - **Control flow statements** such as `if`/`else` or `when`
 * - **Return statements**, including labeled returns
 * - **Code blocks**, such as lambda expressions or loops
 *
 * ### 2. Constructing a code flow graph
 *
 * From the **simplified AST**, we construct a **code flow graph** to model all possible execution
 * paths within the method. In this graph:
 * - **Nodes** represent executable elements such as composable calls and conditionals
 * - **Edges** represent control flow transitions between nodes (e.g., branching or sequential
 *   execution)
 *
 * The graph is **acyclic** because we skip loops and treat all blocks as executing sequentially
 *
 * ### 3. Modifier Reuse Detection
 *
 * Using the **code flow graph**, we analyze each execution path with a **depth-first search** to
 * identify paths containing two or more composable function calls that read the `modifier`
 * parameter. Then we extract `modifier` references from these calls and report them as errors.
 *
 * ---
 *
 * ## Example
 *
 * Consider the following function:
 * ```kotlin
 * @Composable
 * fun Component(modifier: Modifier = Modifier) {
 *     if (condition) {
 *         Text("Text", modifier.fillMaxWidth())
 *         return
 *     }
 *     Row(modifier = modifier) {
 *         Text("Other text", Modifier.weight(1f))
 *         Icon(Icons.Default.Home, modifier.padding(start = 8.dp))
 *     }
 * }
 * ```
 *
 * The **UAST** represents the full structure of the code:
 * ```
 * UMethod (name = Component)
 *     UAnnotation (fqName = androidx.compose.runtime.Composable)
 *     UParameter (name = modifier)
 *         UAnnotation (fqName = org.jetbrains.annotations.NotNull)
 *         USimpleNameReferenceExpression (identifier = Modifier)
 *     UBlockExpression
 *         UIfExpression
 *             USimpleNameReferenceExpression (identifier = condition)
 *             UBlockExpression
 *                 UCallExpression (kind = UastCallKind(name='method_call'), argCount = 2)
 *                     UIdentifier (Identifier (Text))
 *                     ULiteralExpression (value = "Text")
 *                     UQualifiedReferenceExpression
 *                         USimpleNameReferenceExpression (identifier = modifier)
 *                         UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0)
 *                             UIdentifier (Identifier (fillMaxWidth))
 *                 UReturnExpression
 *         ...
 * ```
 *
 * The **simplified AST** removes unnecessary detail while retaining critical nodes for the
 * following analysis:
 * ```
 * CfaBlock (#1)
 * +-- CfaSwitch
 * |   +-- CfaBlock (#2)
 * |   |    +-- CfaCall (source = KtCallExpression("Text"))
 * |   |    \-- CfaJump (block = #1)
 * |   \-- CfaBlock (#3)
 * +-- CfaCall (source = KtCallExpression("Row"))
 * \-- CfaBlock (#4)
 *     +-- CfaCall (source = KtCallExpression("Icon"))
 *     \-- CfaJump (block = #4)
 * ```
 *
 * Using the Simplified AST, we construct the **code flow graph**:
 * - Start and End nodes explicitly mark the entry and exit points of the method
 * - Conditional nodes represent branching points in the control flow
 * - Call nodes capture composable calls that use the modifier parameter
 *
 * ```
 * Start --> Switch ---------------> Call(Text) ----------------------↴
 *             └---------> Call(Row) ---------> Call(Icon) ---------> End
 * ```
 *
 * Finally, we analyze the graph for `modifier` reuse:
 * - **Top path**: one `modifier` read in `Text`
 * - **Bottom path**: two `modifier` reads in `Row` and `Icon`
 *
 * Based on this analysis, we report `Row` and `Icon` modifier arguments as errors:
 * ```kotlin
 * @Composable
 * fun Component(modifier: Modifier = Modifier) {
 *     if (condition) {
 *         Text("Text", modifier.fillMaxWidth())
 *         return
 *     }
 *     Row(modifier = modifier) {
 *                    ~~~~~~~~
 *         Text("Other text", Modifier.weight(1f))
 *         Icon(Icons.Default.Home, modifier.padding(start = 8.dp))
 *                                  ~~~~~~~~
 *     }
 * }
 * ```
 */
class ModifierReusedDetector
@JvmOverloads
constructor(
  private val contentEmitterOption: ContentEmitterLintOption =
    ContentEmitterLintOption(CONTENT_EMITTER_OPTION)
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
    val cfaAst = method.toSimplifiedCfaAst(modifierReferences)
    val (graph, firstNode) = cfaAst.toCodeFlowGraph()
    if (testCodeGraph) codeFlowGraph = graph
    val result = findCallsWithMultipleModifierUses(firstNode, graph)

    result
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

  // Finds all references to modifier parameter, even if it is reassigned to other variable
  private fun findAllParameterReferences(parameter: UParameter, method: UMethod): Set<PsiElement> {
    val modifierReferences = mutableSetOf<PsiElement>()
    parameter.sourcePsi?.let { modifierReferences.add(it) }
    // Analyze data flow to get all possible references of modifier parameter
    method.accept(
      object : DataFlowAnalyzer(setOf(parameter)) {
        override fun receiver(call: UCallExpression) {
          val reference = call.receiver?.skipParenthesizedExprDown()?.sourcePsi
          if (reference is KtSimpleNameExpression) {
            modifierReferences += reference
          }
        }

        override fun argument(call: UCallExpression, reference: UElement) {
          // if modifier is an argument of the call (i.e. Modifier.then(modifier)), then track
          // that call as if it is returning this modifier
          track(call)
          val referenceSource = reference.sourcePsi
          if (referenceSource is KtSimpleNameExpression) {
            modifierReferences += referenceSource
          }
        }
      }
    )
    return modifierReferences
  }

  // Converts UAST into a **simplified AST**
  private fun UMethod.toSimplifiedCfaAst(references: Set<PsiElement>): CfaBlock {
    val blockStack =
      object {

        private val stack = mutableListOf(mutableListOf<CfaNode>())

        fun addNode(node: CfaNode) = run { stack.last() += node }

        fun enterBlock() = stack.add(mutableListOf())

        fun exitBlock(): List<CfaNode> = stack.removeLast()

        fun finalizeBlock(): List<CfaNode> = stack.single()

        inline fun listOfExpressions(body: () -> Unit): List<CfaNode> {
          enterBlock()
          body()
          return exitBlock()
        }
      }

    val blocks = HashMap</* UBlockExpression | UExpressionList */ UExpression, CfaBlock>()
    val jumps = mutableListOf<Pair<CfaJump, UReturnExpression>>()

    accept(
      object : AbstractUastVisitor() {

        // Convert blocks, such as UBlockExpression and UExpressionList, into CfaBlock
        override fun visitBlockExpression(node: UBlockExpression): Boolean {
          blockStack.enterBlock()
          return false
        }

        // Upon exiting from block, remember UAST -> CfaBlock mapping,
        // to let returns know their block later
        override fun afterVisitBlockExpression(node: UBlockExpression) {
          val newBlock = CfaBlock(blockStack.exitBlock())
          blockStack.addNode(newBlock)
          blocks[node] = newBlock
        }

        // Treat UExpressionList same as UBlockExpression
        override fun visitExpressionList(node: UExpressionList): Boolean {
          blockStack.enterBlock()
          return false
        }

        override fun afterVisitExpressionList(node: UExpressionList) {
          val block = CfaBlock(blockStack.exitBlock())
          blockStack.addNode(block)
          blocks[node] = block
        }

        // Convert if into CfaSwitch
        override fun visitIfExpression(node: UIfExpression): Boolean {
          val whenBranches =
            blockStack
              .listOfExpressions {
                node.thenExpression?.accept(this) ?: blockStack.addNode(CfaBlock())
                node.elseExpression?.accept(this) ?: blockStack.addNode(CfaBlock())
              }
              .map { if (it is CfaBlock) it else CfaBlock(it) }

          val switch = CfaSwitch(branches = whenBranches, node.sourcePsi as? KtElement)
          blockStack.addNode(switch)
          return true // we already visited then and else, stop visiting
        }

        // Convert when into CfaSwitch
        override fun visitSwitchExpression(node: USwitchExpression): Boolean {
          val whenBranches =
            blockStack
              .listOfExpressions {
                node.body.expressions.forEach { expression -> expression.accept(this) }
              }
              .map { if (it is CfaBlock) it else CfaBlock(it) }
          val branches = whenBranches + /* "branch" that skips when */ CfaBlock()

          val switch = CfaSwitch(branches, node.sourcePsi as? KtElement)
          blockStack.addNode(switch)

          return true // we already visited all branches, stop visiting
        }

        // Returns are inside blocks, we need to remember them,
        // and link them with blocks after visit
        override fun afterVisitReturnExpression(node: UReturnExpression) {
          val jumpNode = CfaJump()
          blockStack.addNode(jumpNode)
          jumps += jumpNode to node
        }

        // We are interested only in composable calls that use our modifier parameter
        override fun visitCallExpression(node: UCallExpression): Boolean {
          val ktCallExpression = node.sourcePsi
          if (
            ktCallExpression is KtCallExpression && ktCallExpression.isUsingModifiers(references)
          ) {
            val isComposable = (node.resolve().toUElementOfType<UMethod>())?.isComposable ?: false
            if (isComposable) {
              val callNode = CfaCall(ktCallExpression)
              blockStack.addNode(callNode)
            }
          }
          return false
        }
      }
    )

    // Find and fill in blocks that correspond to return's jumpTarget
    jumps.forEach { (returnNode, returnUNode) ->
      var block: UExpression? = null
      returnUNode.jumpTarget2!!.accept(
        object : AbstractUastVisitor() {
          override fun visitBlockExpression(node: UBlockExpression): Boolean {
            block = node
            return true // found block, finish traversal
          }

          override fun visitExpressionList(node: UExpressionList): Boolean {
            block = node
            return true // found block, finish traversal
          }
        }
      )
      val owner = blocks[block]
      if (owner != null) {
        returnNode.block = owner
      }
    }

    return CfaBlock(blockStack.finalizeBlock())
  }

  // Converts **simplified AST** to a **code flow graph**
  // Returns pair of <graph adjacency list, start node of method>
  private fun CfaBlock.toCodeFlowGraph(): Pair<Map<GraphNode, Set<GraphNode>>, GraphNode> {
    val graph = HashMap<GraphNode, MutableSet<GraphNode>>()
    fun GraphNode.link(other: GraphNode?) {
      if (other != null) {
        graph.getOrPut(this) { mutableSetOf() }.add(other)
      }
    }

    // We remember block exit nodes before visiting them, for returns to know where to jump to
    val blockExits = HashMap<CfaBlock, GraphNode>()

    val endNode = GraphNode(id = "end")
    var followingNode: GraphNode = endNode

    /**
     * - Walk backwards, from method finish to beginning
     * - Link [this] node to the followingNode
     * - Update followingNode to point to a start node of this node
     */
    fun CfaNode.visitRecursive() {
      when (val node = this) {
        is CfaCall -> {
          val graphNode = GraphNode(call = node.source, id = node.source.lineInFile())
          graphNode.link(followingNode)
          followingNode = graphNode
        }

        is CfaSwitch -> {
          val switchNode = GraphNode(isSwitch = true, id = node.source.lineInFile())
          val joinNode = followingNode
          for (branch in node.branches) {
            followingNode = joinNode
            branch.visitRecursive()
            switchNode.link(followingNode)
          }
          followingNode = switchNode
        }

        is CfaJump -> {
          followingNode = blockExits[node.block]!!
        }

        is CfaBlock -> {
          blockExits[node] = followingNode
          for (child in node.nodes.asReversed()) {
            child.visitRecursive()
          }
        }
      }
    }
    visitRecursive()

    // The start node is not necessary, but it is easier for debugging
    val startNode = GraphNode(id = "start")
    startNode.link(followingNode)

    return graph to startNode
  }

  // Detects modifier reuses in graph using depth-first search
  private fun findCallsWithMultipleModifierUses(
    firstNode: GraphNode,
    graph: Map<GraphNode, Set<GraphNode>>,
  ): List<KtCallExpression> {
    val currentPath = mutableListOf<GraphNode>()
    val result = mutableSetOf<GraphNode>()
    fun findPathsWithMultipleModifierUses(
      currentNode: GraphNode,
      graph: Map<GraphNode, Set<GraphNode>>,
    ) {
      graph[currentNode]?.forEach { adjacent ->
        if (adjacent.call != null) currentPath.add(adjacent)
        if (currentPath.size >= 2) {
          // this code path leads to using modifier parameter twice - report
          result.addAll(currentPath)
        }
        findPathsWithMultipleModifierUses(adjacent, graph)
        if (adjacent.call != null) currentPath.removeAt(currentPath.lastIndex)
      }
    }
    findPathsWithMultipleModifierUses(firstNode, graph)
    return result.map { it.call!! }
  }

  // A **simplified AST** node
  private sealed class CfaNode

  // Composable call, that uses modifier parameter
  private class CfaCall(val source: KtCallExpression) : CfaNode()

  // Return from a lambda or a function
  private class CfaJump(var block: CfaBlock? = null) : CfaNode()

  // List of nodes, may be empty
  private class CfaBlock(val nodes: List<CfaNode>) : CfaNode() {
    constructor(vararg nodes: CfaNode) : this(nodes.toList())
  }

  // if/else or when statements
  private class CfaSwitch(val branches: List<CfaBlock>, val source: KtElement?) : CfaNode()

  // A **code flow graph** node
  class GraphNode(
    val call: KtCallExpression? = null,
    id: String? = null,
    isSwitch: Boolean = false,
  ) {

    private val id = if (isSwitch) "Switch $id" else "Node $id"

    override fun toString(): String = id
  }

  private fun KtCallExpression.isUsingModifiers(references: Set<PsiElement>): Boolean {
    var usesModifier = false
    valueArgumentList?.visitReferencesSkipLambdas { reference ->
      if (reference in references) {
        usesModifier = true
      }
    }
    return usesModifier
  }

  private inline fun KtElement.visitReferencesSkipLambdas(
    crossinline visitReference: (KtReferenceExpression) -> Unit
  ) {
    acceptChildren(
      object : KtTreeVisitorVoid() {
        override fun visitReferenceExpression(expression: KtReferenceExpression) {
          super.visitReferenceExpression(expression)
          visitReference(expression)
        }

        override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) = Unit
      }
    )
  }

  // Copy-paste from KotlinUReturnExpression
  // Fixes jumpTarget for lambda in a constructor call (for example in CustomAccessibilityAction)
  // Issue https://issuetracker.google.com/issues/381518932
  @Suppress("UnstableApiUsage")
  private val UReturnExpression.jumpTarget2: UElement?
    get() {
      if (this !is KotlinUReturnExpression) return jumpTarget
      return generateSequence(uastParent) { it.uastParent }
        .find {
          it is ULabeledExpression && it.label == label ||
            it is UMethod && it.name == label ||
            (it is UMethod || it is KotlinLocalFunctionULambdaExpression) && label == null ||
            (it is ULambdaExpression &&
              it.uastParent.let { parent ->
                parent is UCallExpression &&
                  (parent.methodName == label || parent.methodIdentifier?.name == label)
              })
        }
    }

  private fun KtElement?.lineInFile(): String? {
    if (!testCodeGraph || this == null) return null
    return DiagnosticUtils.getLineAndColumnInPsiFile(containingFile, textRange).line.toString()
  }
}
