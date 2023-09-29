// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtParenthesizedExpression

inline fun <reified T : PsiElement> PsiElement.findChildrenByClass(): Sequence<T> {
  val expr = unwrapParenthesis() ?: return emptySequence()
  return sequence {
    val queue = ArrayDeque<PsiElement>()
    queue.add(expr)
    while (queue.isNotEmpty()) {
      val current = queue.removeFirst().unwrapParenthesis() ?: continue
      if (current is T) {
        yield(current)
      }
      queue.addAll(current.children)
    }
  }
}

inline fun <reified T : PsiElement> PsiElement.findDirectChildrenByClass(): Sequence<T> {
  val expr = unwrapParenthesis() ?: return emptySequence()
  return sequence {
    var current = expr.firstChild.unwrapParenthesis()
    while (current != null) {
      if (current is T) {
        yield(current)
      }
      current = current.nextSibling.unwrapParenthesis()
    }
  }
}

@PublishedApi
internal fun PsiElement?.unwrapParenthesis(): PsiElement? {
  return when (this) {
    null -> null
    is KtParenthesizedExpression -> expression
    else -> this
  }
}
