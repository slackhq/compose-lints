// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression

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
    var current = expr.firstChild?.unwrapParenthesis()
    while (current != null) {
      if (current is T) {
        yield(current)
      }
      current = current.nextSibling?.unwrapParenthesis()
    }
  }
}

@PublishedApi
internal fun PsiElement?.unwrapParenthesis(): PsiElement? {
  return when (this) {
    null -> null
    is KtExpression -> unwrapParenthesis()
    else -> this
  }
}

@PublishedApi
internal fun KtExpression.unwrapParenthesis(): KtExpression? {
  return when (this) {
    is KtParenthesizedExpression -> expression
    else -> this
  }
}

@PublishedApi
internal fun KtExpression.unwrapBlock(): KtExpression? {
  return when (this) {
    is KtBlockExpression -> firstStatement
    else -> this
  }
}

@PublishedApi
internal fun KtExpression.unwrapReturnExpression(): KtExpression? {
  return when (this) {
    is KtReturnExpression -> returnedExpression
    else -> this
  }
}
