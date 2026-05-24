// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.sourceImplementation

/**
 * Flags `hashCode()` calls used in the `key` of `Lazy*`/`Pager` composables (`LazyColumn`,
 * `LazyVerticalGrid`, `HorizontalPager`, `items`, `itemsIndexed`, `item`, …).
 *
 * Item keys must be unique, but [hashCode] is never guaranteed to be unique. `Lazy*` layouts throw
 * at runtime when they detect duplicate keys, so a `hashCode`-based key is a latent crash waiting
 * on the right (or wrong) data.
 *
 * This is intentionally syntactic: within a `@Composable` function (via
 * [ComposableFunctionDetector]) it fires on any argument named `key` (the parameter name used by
 * every relevant API) that contains a `hashCode()` call. Scoping to composables filters out
 * unrelated functions that happen to have a `key` parameter. A `key` passed positionally is not
 * caught, but the APIs steer callers toward naming it.
 */
class ItemKeyHashCodeDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    val ISSUE =
      Issue.create(
        id = "ComposeItemKeyHashCode",
        briefDescription = "Don't use hashCode as an item key",
        explanation =
          """
            Item keys in `Lazy*`/`Pager`/etc. layouts must be unique, but `hashCode()` is never \
            guaranteed to be unique. `Lazy*` layouts throw at runtime when they encounter duplicate \
            keys, so a `hashCode`-based key can crash as soon as data with a colliding hash comes \
            along. Use a genuinely unique, stable identifier instead (e.g. a server-provided id). \
            See https://slackhq.github.io/compose-lints/rules/#dont-use-hashcode-as-a-key for more information.
          """,
        category = Category.CORRECTNESS,
        priority = Priorities.NORMAL,
        severity = Severity.WARNING,
        implementation = sourceImplementation<ItemKeyHashCodeDetector>(),
      )
  }

  override fun visitComposable(context: JavaContext, method: UMethod) {
    // Walk this composable's body for calls with a `key` argument and flag any hashCode() inside
    // it.
    method.uastBody?.accept(
      object : AbstractUastVisitor() {
        // Don't descend into nested declarations; the base class visits those composables
        // separately.
        override fun visitMethod(node: UMethod): Boolean = true

        override fun visitCallExpression(node: UCallExpression): Boolean {
          val call = node.sourcePsi as? KtCallExpression ?: return false
          // Find the argument bound to a parameter named "key". This covers both the lambda form
          // (`items(list, key = { it.hashCode() })`) and the direct-value form
          // (`item(key = it.hashCode())`).
          val keyExpression =
            call.valueArguments
              .firstOrNull { it.getArgumentName()?.asName?.asString() == "key" }
              ?.getArgumentExpression()
              ?.toUElement() ?: return false

          keyExpression.accept(
            object : AbstractUastVisitor() {
              override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "hashCode" && node.valueArgumentCount == 0) {
                  context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    ISSUE.getExplanation(TextFormat.TEXT),
                  )
                }
                return false
              }
            }
          )
          return false
        }
      }
    )
  }
}
