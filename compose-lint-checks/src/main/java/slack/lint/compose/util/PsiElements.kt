// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import java.util.Deque
import java.util.LinkedList
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.kotlin.unwrapBlockOrParenthesis

inline fun <reified T : PsiElement> PsiElement.findChildrenByClass(): Sequence<T> = sequence {
  val queue: Deque<PsiElement> = LinkedList()
  queue.add(this@findChildrenByClass)
  while (queue.isNotEmpty()) {
    val current = queue.pop()
    if (current is T) {
      yield(current)
    }
    queue.addAll(current.children)
  }
}

inline fun <reified T : PsiElement> PsiElement.findDirectFirstChildByClass(): T? {
  var current = firstChild
  while (current != null) {
    if (current is T) {
      return current
    }
    current = current.nextSibling
  }
  return null
}

inline fun <reified T : PsiElement> PsiElement.findDirectChildrenByClass(): Sequence<T> {
  var unwrapped = false
  val expr =
    if (this is KtExpression) {
      unwrapped = true
      unwrapBlockOrParenthesis()
    } else {
      this
    }
  return sequence {
    if (unwrapped && expr is T) {
      yield(expr)
    }
    var current = expr.firstChild
    while (current != null) {
      if (current is T) {
        yield(current)
      }
      current = current.nextSibling
    }
  }
}

val PsiNameIdentifierOwner.startOffsetFromName: Int
  get() = nameIdentifier?.startOffset ?: startOffset
