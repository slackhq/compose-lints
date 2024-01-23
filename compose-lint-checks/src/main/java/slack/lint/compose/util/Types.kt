package slack.lint.compose.util

import com.intellij.psi.PsiClass

val PsiClass.isFunctionalInterface: Boolean
  get() {
    return hasAnnotation("java.lang.FunctionalInterface") ||
      qualifiedName == "kotlin.Function" ||
      qualifiedName?.startsWith("kotlin.jvm.functions.") == true
  }