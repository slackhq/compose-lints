// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import java.util.Deque
import java.util.LinkedList
import org.jetbrains.kotlin.psi.psiUtil.startOffset

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

inline fun <reified T : PsiElement> PsiElement.findDirectChildrenByClass(): Sequence<T> = sequence {
  var current = firstChild
  while (current != null) {
    if (current is T) {
      yield(current)
    }
    current = current.nextSibling
  }
}

val PsiNameIdentifierOwner.startOffsetFromName: Int
  get() = nameIdentifier?.startOffset ?: startOffset
