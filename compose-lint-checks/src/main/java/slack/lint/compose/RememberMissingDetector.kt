// Copyright (C) 2023 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.findChildrenByClass
import slack.lint.compose.util.sourceImplementation

class RememberMissingDetector : ComposableFunctionDetector(), SourceCodeScanner {

  companion object {
    private fun errorMessage(name: String): String =
      """
        Using `$name` in a @Composable function without it being inside of a remember function.
        If you don't remember the state instance, a new state instance will be created when the function is recomposed.
        See https://slackhq.github.io/compose-lints/rules/#state-should-be-remembered-in-composables for more information.
      """
        .trimIndent()

    private val MethodsThatNeedRemembering = setOf("derivedStateOf", "mutableStateOf")
    private val QualifiedMethodsThatNeedRemembering =
      setOf("androidx.compose.runtime.mutableStateOf", "androidx.compose.runtime.derivedStateOf")
    val DerivedStateOfNotRemembered = errorMessage("derivedStateOf")
    val MutableStateOfNotRemembered = errorMessage("mutableStateOf")

    val ISSUE =
      Issue.create(
        id = "ComposeRememberMissing",
        briefDescription = "State values should be remembered",
        explanation = "This is replaced when reported",
        category = Category.PRODUCTIVITY,
        priority = Priorities.NORMAL,
        severity = Severity.ERROR,
        implementation = sourceImplementation<RememberMissingDetector>(),
      )
  }

  private fun KtCallExpression.resolvedQualifiedName(): String? {
    return calleeExpression?.toUElement()?.tryResolve()?.let { resolved ->
      (resolved as? com.intellij.psi.PsiMethod)?.containingClass?.qualifiedName?.let { className ->
        "$className.${(resolved as com.intellij.psi.PsiNamedElement).name}"
      }
        ?: (resolved as? com.intellij.psi.PsiNamedElement)?.let { named ->
          (resolved as? com.intellij.psi.PsiMember)?.containingClass?.qualifiedName?.let {
            "$it.${named.name}"
          }
        }
    }
  }

  private fun KtCallExpression.matchesNeedRemembering(): String? {
    // Try simple name first for performance
    val simpleName = calleeExpression?.text
    if (simpleName != null && MethodsThatNeedRemembering.contains(simpleName)) return simpleName
    // Fall back to resolved qualified name for import aliases
    val qualifiedName = resolvedQualifiedName() ?: return null
    return when {
      qualifiedName.endsWith(".mutableStateOf") &&
        QualifiedMethodsThatNeedRemembering.any {
          qualifiedName.endsWith(it.substringAfterLast("."))
        } -> "mutableStateOf"
      qualifiedName.endsWith(".derivedStateOf") &&
        QualifiedMethodsThatNeedRemembering.any {
          qualifiedName.endsWith(it.substringAfterLast("."))
        } -> "derivedStateOf"
      else -> null
    }
  }

  override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {
    // To keep memory consumption in check, we first traverse down until we see one of our known
    // functions
    // that need remembering
    function
      .findChildrenByClass<KtCallExpression>()
      .mapNotNull { call -> call.matchesNeedRemembering()?.let { call to it } }
      // Only for those, we traverse up to [function], to see if it was actually remembered
      .filterNot { (call, _) -> call.isRemembered(function) }
      // If it wasn't, we show the error
      .forEach { (callExpression, resolvedName) ->
        when (resolvedName) {
          "mutableStateOf" -> {
            context.report(
              ISSUE,
              callExpression,
              context.getLocation(callExpression),
              MutableStateOfNotRemembered,
            )
          }
          "derivedStateOf" -> {
            context.report(
              ISSUE,
              callExpression,
              context.getLocation(callExpression),
              DerivedStateOfNotRemembered,
            )
          }
        }
      }
  }

  private fun KtCallExpression.isRemembered(stopAt: PsiElement): Boolean {
    var current: PsiElement = parent
    while (!current.isEquivalentTo(stopAt)) {
      (current as? KtCallExpression)?.let { callExpression ->
        val simpleName = callExpression.calleeExpression?.text
        if (simpleName?.startsWith("remember") == true) return true
        // Resolve for import aliases
        val resolved = callExpression.calleeExpression?.toUElement()?.tryResolve()
        val resolvedName = (resolved as? com.intellij.psi.PsiNamedElement)?.name
        if (resolvedName?.startsWith("remember") == true) return true
      }
      current = current.parent
    }
    return false
  }
}
