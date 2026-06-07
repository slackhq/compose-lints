// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import slack.lint.compose.ItemKeyNotSaveableDetector.Companion.RESTRICTED_SCOPES
import slack.lint.compose.ItemKeyNotSaveableDetector.Companion.SAVEABLE_TYPES
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.allSupertypes
import slack.lint.compose.util.isFunctionalInterface
import slack.lint.compose.util.sourceImplementation

/**
 * Flags lazy-layout `key` values whose type can't be saved to a `Bundle`. Item keys are persisted
 * across configuration changes and process death, so on Android they must be a primitive,
 * `String`/`CharSequence`, `Serializable`, or `Parcelable`; anything else crashes at runtime once
 * the key is saved and restored.
 *
 * Scoping is by receiver type (see [RESTRICTED_SCOPES]), which uniformly covers interface members
 * (`item`, `items(count, ...)`) and extensions (`items(List)`, `itemsIndexed`) without firing on
 * unrelated APIs that happen to take a `key`. The `key` argument is found via parameter mapping (so
 * named/positional/reordered all work), then its type is checked in [isSaveable] - conservatively,
 * reporting only types that provably aren't saveable.
 */
class ItemKeyNotSaveableDetector : ComposableFunctionDetector(), SourceCodeScanner {
  companion object {
    /**
     * Receiver types whose `key` carries the "must be saveable via `Bundle`" restriction; a call is
     * checked only when invoked on one of these (or a subtype). Sourced from every AndroidX scope
     * documenting that restriction; extensions (paging, Wear `expandable*`) are covered
     * transitively via the shared receiver. Pager is intentionally absent - it has no restriction.
     */
    private val RESTRICTED_SCOPES =
      setOf(
        "androidx.compose.foundation.lazy.LazyListScope",
        "androidx.compose.foundation.lazy.grid.LazyGridScope",
        "androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope",
        "androidx.wear.compose.foundation.lazy.ScalingLazyListScope",
        "androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope",
        "androidx.wear.compose.material.ScalingLazyListScope", // deprecated material variant
        "androidx.xr.glimmer.list.GlimmerLazyListScope",
      )

    private val SAVEABLE_TYPES =
      setOf(
        "android.os.Bundle",
        "android.os.IBinder",
        "android.os.Parcelable",
        "android.util.Size",
        "android.util.SizeF",
        "android.util.SparseArray",
        "java.io.Serializable",
        "java.lang.CharSequence",

        // These are included even though their static type isn't Serializable, because stdlib
        // factories (listOf/mapOf/...) return Serializable impls. Flagging them would give a
        // false-positive on the common "composite key built from a list" pattern
        "java.util.Collection",
        "java.util.Map",
        "kotlin.collections.Collection",
        "kotlin.collections.Map",
      )

    val ISSUE =
      Issue.create(
        id = "ComposeItemKeyNotSaveable",
        briefDescription = "Item key type must be saveable in a Bundle",
        explanation =
          issueText(
            """
            Item keys in `Lazy*` layouts can be persisted across configuration changes and process
            death, so on Android the type of the key should be saveable via `Bundle`: a primitive,
            `String`/`CharSequence`, `Serializable`, or `Parcelable`. A key whose type is none of
            these can crash at runtime when the key is saved and restored (e.g. when item state is
            stored via `rememberSaveable`).

            Use a saveable identifier instead (e.g. a `String` or `Int` id), or make the key type
            `Parcelable`/`Serializable`.

            See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information.
            """
          ),
        category = Category.CORRECTNESS,
        priority = Priorities.NORMAL,
        severity = Severity.WARNING,
        implementation = sourceImplementation<ItemKeyNotSaveableDetector>(),
      )
  }

  override fun visitComposable(context: JavaContext, method: UMethod) {
    method.uastBody?.accept(
      object : AbstractUastVisitor() {
        override fun visitMethod(node: UMethod): Boolean = true

        override fun visitCallExpression(node: UCallExpression): Boolean {
          if (!node.isRestrictedScopeCall()) return false
          // Resolve the argument bound to the key parameter via the parameter mapping rather than
          // matching a literal "key =" name, so positionally-passed and reordered keys are covered
          val method = node.resolve() ?: return false
          val keyArgument =
            context.evaluator
              .computeArgumentMapping(node, method)
              .entries
              .firstOrNull { (_, parameter) -> parameter.name == "key" }
              ?.key ?: return false

          val keyExpression =
            when (keyArgument) {
              // The lambda form carries the type on the returned expression
              is ULambdaExpression -> keyArgument.returnedExpression ?: return false
              // The direct-value form carries it on the argument itself
              else -> keyArgument
            }

          val keyType = keyExpression.getExpressionType() ?: return false
          if (!keyType.isSaveable()) {
            context.report(
              issue = ISSUE,
              scope = keyExpression,
              location = context.getLocation(keyExpression),
              message = ISSUE.getExplanation(TextFormat.TEXT),
            )
          }
          return false
        }
      }
    )
  }

  /**
   * Whether this call is invoked on a [RESTRICTED_SCOPES] receiver (or a subtype). Covers both
   * interface members and extension functions, since both report the scope as the receiver type.
   */
  private fun UCallExpression.isRestrictedScopeCall(): Boolean {
    val receiver = (receiverType as? PsiClassType)?.resolve() ?: return false
    return receiver.allSupertypes.any { it.qualifiedName in RESTRICTED_SCOPES }
  }

  /** The expression whose value a lambda returns (its last statement / explicit return). */
  private val ULambdaExpression.returnedExpression: UExpression?
    get() {
      val last = (body as? UBlockExpression)?.expressions?.lastOrNull() ?: return null
      return if (last is UReturnExpression) last.returnExpression else last
    }

  /**
   * Whether [this] type can be persisted to a `Bundle`: a primitive, an array whose component type
   * is saveable, or a class assignable to one of [SAVEABLE_TYPES]. Collection element types aren't
   * inspected (not reliably reachable through generics), so `key = { listOf(notSaveable) }` passes.
   *
   * Returns true for types it can't analyze, keeping the rule conservative: function types (a
   * stored selector or reference, where the real key type is out of reach) and `Any`/`Object`
   * (whose static type says nothing about the runtime value).
   */
  private fun PsiType.isSaveable(): Boolean =
    when (this) {
      is PsiPrimitiveType -> true
      is PsiArrayType -> componentType.isSaveable()
      is PsiClassType -> {
        val resolved = resolve()
        resolved == null ||
          resolved is PsiTypeParameter ||
          resolved.qualifiedName == CommonClassNames.JAVA_LANG_OBJECT ||
          resolved.isFunctionalInterface ||
          resolved.allSupertypes.any { it.qualifiedName in SAVEABLE_TYPES }
      }
      else -> true
    }
}
