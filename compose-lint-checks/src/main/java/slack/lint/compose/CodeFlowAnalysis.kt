// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.DiagnosticUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
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
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.kotlin.KotlinLocalFunctionULambdaExpression
import org.jetbrains.uast.kotlin.KotlinUReturnExpression
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor
import slack.lint.compose.util.isComposable

/**
 * **Code flow analysis** that identifies execution paths within a method that read the `modifier`
 * parameter multiple times. This is the machinery behind [ModifierReusedDetector]; it is kept in
 * its own file because the simplified-AST/graph construction adds a fair amount of complexity that
 * is orthogonal to the lint check itself.
 *
 * ---
 *
 * ## Overview
 *
 * ### 1. Converting UAST to a simplified AST
 *
 * The **UAST** provides a detailed representation of code, including annotations, identifiers,
 * references, and expressions. However, for this analysis, most of these details are irrelevant. To
 * simplify the analysis, we convert **UAST** into a **simplified AST** ([CfaNode]), which retains
 * only the nodes needed for code flow analysis:
 * - **Composable function calls** that use the `modifier` parameter ([CfaCall])
 * - **Control flow statements** such as `if`/`else` or `when` ([CfaSwitch])
 * - **Return and throw statements**, including labeled returns ([CfaJump])
 * - **Code blocks**, such as lambda expressions or loops ([CfaBlock])
 *
 * ### 2. Constructing a code flow graph
 *
 * From the **simplified AST**, we construct a **code flow graph** ([CodeFlowGraph]) to model all
 * possible execution paths within the method. In this graph:
 * - **Nodes** ([GraphNode]) represent executable elements such as composable calls and conditionals
 * - **Edges** represent control flow transitions between nodes (e.g., branching or sequential
 *   execution)
 *
 * The graph is **acyclic** because we skip loops and treat all blocks as executing sequentially
 *
 * ### 3. Modifier Reuse Detection
 *
 * Using the **code flow graph**, we analyze each execution path with a **depth-first search** to
 * identify paths containing two or more composable function calls that read the `modifier`
 * parameter ([CodeFlowGraph.findCallsWithMultipleModifierUses]). The caller then extracts
 * `modifier` references from these calls and reports them as errors.
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
 *
 * @param modifierReferences all PSI references that resolve to the `modifier` parameter (including
 *   reassignments), used to decide which calls read the modifier.
 * @param computeNodeIds when true, [GraphNode]s are tagged with their source line so the resulting
 *   graph can be asserted in tests; this is purely cosmetic and skipped in production.
 */
internal fun UMethod.buildCodeFlowGraph(
  modifierReferences: Set<PsiElement>,
  computeNodeIds: Boolean = false,
): CodeFlowGraph {
  val ast = toSimplifiedCfaAst(modifierReferences)
  return ast.toCodeFlowGraph(computeNodeIds)
}

/** A **code flow graph**: an adjacency list over [GraphNode]s rooted at [startNode]. */
internal class CodeFlowGraph(
  val startNode: GraphNode,
  val adjacency: Map<GraphNode, Set<GraphNode>>,
) {
  /**
   * Detects modifier reuses in the graph using a depth-first search: any execution path that visits
   * two or more composable calls reading the modifier parameter is a reuse, and every such call on
   * those paths is returned.
   */
  fun findCallsWithMultipleModifierUses(): List<KtCallExpression> {
    val currentPath = mutableListOf<GraphNode>()
    val result = mutableSetOf<GraphNode>()
    fun visit(currentNode: GraphNode) {
      adjacency[currentNode]?.forEach { adjacent ->
        if (adjacent.call != null) currentPath.add(adjacent)
        if (currentPath.size >= 2) {
          // this code path leads to using modifier parameter twice - report
          result.addAll(currentPath)
        }
        visit(adjacent)
        if (adjacent.call != null) currentPath.removeAt(currentPath.lastIndex)
      }
    }
    visit(startNode)
    return result.map { it.call!! }
  }
}

/** A **code flow graph** node. */
class GraphNode(val call: KtCallExpression? = null, id: String? = null, isSwitch: Boolean = false) {

  private val id = if (isSwitch) "Switch $id" else "Node $id"

  override fun toString(): String = id
}

/** A **simplified AST** node. */
private sealed interface CfaNode

/** Composable call that uses the modifier parameter. */
private class CfaCall(val source: KtCallExpression) : CfaNode

/** Return from a lambda or a function, or a throw that exits the function. */
private class CfaJump(var block: CfaBlock? = null) : CfaNode

/** List of nodes, may be empty. */
private class CfaBlock(val nodes: List<CfaNode>) : CfaNode {
  constructor(vararg nodes: CfaNode) : this(nodes.toList())
}

/** if/else or when statements. */
private class CfaSwitch(val branches: List<CfaBlock>, val source: KtElement?) : CfaNode

/** Converts UAST into a simplified AST. */
private fun UMethod.toSimplifiedCfaAst(references: Set<PsiElement>): CfaBlock {
  val blockStack =
    object {

      private val stack = mutableListOf(mutableListOf<CfaNode>())

      fun addNode(node: CfaNode) = run { stack.last() += node }

      fun enterBlock() = stack.add(mutableListOf())

      // Avoid removeLast() as that's JDK 21+
      fun exitBlock(): List<CfaNode> = stack.removeAt(stack.lastIndex)

      fun finalizeBlock(): List<CfaNode> = stack.single()

      inline fun listOfExpressions(body: () -> Unit): List<CfaNode> {
        enterBlock()
        body()
        return exitBlock()
      }
    }

  val blocks = mutableMapOf</* UBlockExpression | UExpressionList */ UExpression, CfaBlock>()
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
            .map { it as? CfaBlock ?: CfaBlock(it) }

        val switch = CfaSwitch(branches = whenBranches, node.sourcePsi as? KtElement)
        blockStack.addNode(switch)
        // we already visited then and else, stop visiting
        return true
      }

      // Convert when into CfaSwitch
      override fun visitSwitchExpression(node: USwitchExpression): Boolean {
        val whenBranches =
          blockStack
            .listOfExpressions {
              node.body.expressions.forEach { expression -> expression.accept(this) }
            }
            .map { it as? CfaBlock ?: CfaBlock(it) }
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

      // A throw terminates the current execution path. Model it as a jump with no target block,
      // which resolves to the method exit (see CfaJump handling in toCodeFlowGraph). This mirrors
      // a top-level return and prevents reachability false positives for early throws.
      override fun afterVisitThrowExpression(node: UThrowExpression) {
        blockStack.addNode(CfaJump())
      }

      // We are interested only in composable calls that use our modifier parameter
      override fun visitCallExpression(node: UCallExpression): Boolean {
        val ktCallExpression = node.sourcePsi
        if (ktCallExpression is KtCallExpression && ktCallExpression.isUsingModifiers(references)) {
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

/** Converts a **simplified AST** into a [CodeFlowGraph]. */
private fun CfaBlock.toCodeFlowGraph(computeNodeIds: Boolean): CodeFlowGraph {
  val graph = HashMap<GraphNode, MutableSet<GraphNode>>()
  fun GraphNode.link(other: GraphNode?) {
    if (other != null) {
      graph.getOrPut(this, ::mutableSetOf).add(other)
    }
  }

  // We remember block exit nodes before visiting them, for returns to know where to jump to
  val blockExits = mutableMapOf<CfaBlock, GraphNode>()

  val endNode = GraphNode(id = "end")
  var followingNode: GraphNode = endNode

  fun KtElement?.nodeId(): String? = if (computeNodeIds) lineInFile() else null

  /**
   * - Walk backwards, from method finish to beginning
   * - Link [this] node to the followingNode
   * - Update followingNode to point to a start node of this node
   */
  fun CfaNode.visitRecursive() {
    when (val node = this) {
      is CfaCall -> {
        val graphNode = GraphNode(call = node.source, id = node.source.nodeId())
        graphNode.link(followingNode)
        followingNode = graphNode
      }

      is CfaSwitch -> {
        val switchNode = GraphNode(isSwitch = true, id = node.source.nodeId())
        val joinNode = followingNode
        for (branch in node.branches) {
          followingNode = joinNode
          branch.visitRecursive()
          switchNode.link(followingNode)
        }
        followingNode = switchNode
      }

      is CfaJump -> {
        // A return jumps to the exit of its target block; a throw (or an unresolved return) has no
        // target block and exits the method via the end node.
        followingNode = node.block?.let { blockExits[it] } ?: endNode
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

  return CodeFlowGraph(startNode, graph)
}

internal fun KtCallExpression.isUsingModifiers(references: Set<PsiElement>): Boolean {
  var usesModifier = false
  valueArgumentList?.visitReferencesSkipLambdas { reference ->
    if (reference in references) {
      usesModifier = true
    }
  }
  return usesModifier
}

internal inline fun KtElement.visitReferencesSkipLambdas(
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
  if (this == null) return null
  return DiagnosticUtils.getLineAndColumnInPsiFile(containingFile, textRange).line.toString()
}
