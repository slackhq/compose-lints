// Copyright (C) 2022 Salesforce, Inc.
// Copyright 2022 Twitter, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.client.api.JavaEvaluator
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolve

fun KtFunction.emitsContent(providedContentEmitters: Set<String>): Boolean {
  return if (toUElementOfType<UMethod>()?.isComposable == true) {
    sequence {
        tailrec suspend fun SequenceScope<KtCallExpression>.scan(elements: List<PsiElement>) {
          if (elements.isEmpty()) return
          val toProcess =
            elements
              .mapNotNull { current ->
                if (current is KtCallExpression) {
                  if (current.emitExplicitlyNoContent) {
                    null
                  } else {
                    yield(current)
                    current
                  }
                } else {
                  current
                }
              }
              .flatMap { it.children.toList() }
          return scan(toProcess)
        }
        scan(listOf(this@emitsContent))
      }
      .any { it.emitsContent(providedContentEmitters) }
  } else {
    false
  }
}

private val KtCallExpression.emitExplicitlyNoContent: Boolean
  get() = resolvedSimpleName in ComposableNonEmittersList

fun KtCallExpression.emitsContent(providedContentEmitters: Set<String>): Boolean {
  val methodName = resolvedSimpleName ?: return false
  return ComposableEmittersList.contains(methodName) ||
    ComposableEmittersListRegex.matches(methodName) ||
    providedContentEmitters.contains(methodName) ||
    containsComposablesWithModifiers
}

/** Resolves the simple name of the callee, handling import aliases and FQNs via UAST. */
private val KtCallExpression.resolvedSimpleName: String?
  get() {
    val text = calleeExpression?.text ?: return null
    // If it's a plain identifier without dots or import alias prefix, use directly
    if ('.' !in text && !text.startsWith("IMPORT_ALIAS")) return text
    // For fully qualified names, try UAST resolution first
    val resolved =
      calleeExpression?.toUElement()?.tryResolve() as? com.intellij.psi.PsiNamedElement
    if (resolved != null) return resolved.name
    // Fallback: take the last segment of the dot-qualified name
    if ('.' in text) return text.substringAfterLast('.')
    return text
  }

private val KtCallExpression.containsComposablesWithModifiers: Boolean
  get() = valueArguments.filter { it.isNamed() }.any { it.getArgumentName()?.text == "modifier" }

/**
 * This is a denylist with common composables that emit content in their own window. Feel free to
 * add more elements if you stumble upon them in code reviews that should have triggered an error
 * from this rule.
 */
private val ComposableNonEmittersList = setOf("AlertDialog", "ModalBottomSheetLayout")

/**
 * This is an allowlist with common composables that emit content. Feel free to add more elements if
 * you stumble upon them in code reviews that should have triggered an error from this rule.
 */
private val ComposableEmittersList by lazy {
  setOf(
    // androidx.compose.foundation
    "BasicTextField",
    "Box",
    "Canvas",
    "ClickableText",
    "Column",
    "Icon",
    "Image",
    "Layout",
    "LazyColumn",
    "LazyRow",
    "LazyVerticalGrid",
    "Row",
    "Text",
    // android.compose.material
    "BottomDrawer",
    "Button",
    "Card",
    "Checkbox",
    "CircularProgressIndicator",
    "Divider",
    "DropdownMenu",
    "DropdownMenuItem",
    "ExposedDropdownMenuBox",
    "ExtendedFloatingActionButton",
    "FloatingActionButton",
    "IconButton",
    "IconToggleButton",
    "LeadingIconTab",
    "LinearProgressIndicator",
    "ListItem",
    "ModalBottomSheetLayout",
    "ModalDrawer",
    "NavigationRail",
    "NavigationRailItem",
    "OutlinedButton",
    "OutlinedTextField",
    "RadioButton",
    "Scaffold",
    "ScrollableTabRow",
    "Slider",
    "SnackbarHost",
    "Surface",
    "SwipeToDismiss",
    "Switch",
    "Tab",
    "TabRow",
    "TextButton",
    "TopAppBar",
    // Accompanist
    "BottomNavigation",
    "BottomNavigationContent",
    "BottomNavigationSurface",
    "FlowColumn",
    "FlowRow",
    "HorizontalPager",
    "HorizontalPagerIndicator",
    "SwipeRefresh",
    "SwipeRefreshIndicator",
    "TopAppBarContent",
    "TopAppBarSurface",
    "VerticalPager",
    "VerticalPagerIndicator",
    "WebView",
  )
}

val ComposableEmittersListRegex by lazy {
  Regex(
    listOf(
        "Spacer\\d*" // Spacer() + SpacerNUM()
      )
      .joinToString(separator = "|", prefix = "(", postfix = ")")
  )
}

val ModifierNames by lazy(LazyThreadSafetyMode.NONE) { setOf("Modifier", "GlanceModifier") }
val ModifierQualifiedNames by
  lazy(LazyThreadSafetyMode.NONE) {
    setOf("androidx.compose.ui.Modifier", "androidx.glance.GlanceModifier")
  }

val KtCallableDeclaration.isModifier: Boolean
  get() = ModifierNames.contains(typeReference?.text)

fun UParameter.isModifier(evaluator: JavaEvaluator): Boolean {
  (sourcePsi as? KtParameter)?.let {
    if (it.typeReference?.text in ModifierNames) {
      return true
    }
  }
  // Fall back to more thorough approach
  return ModifierQualifiedNames.any { evaluator.typeMatches(type, it) }
}

fun UParameter.isSlotParameter(evaluator: JavaEvaluator): Boolean {
  // Check if the parameter type has a @Composable annotation
  val ktParam = sourcePsi as? KtParameter ?: return false
  val typeRef = ktParam.typeReference ?: return false
  // Check if any annotation on the type reference is @Composable
  val hasComposableAnnotation =
    typeRef.annotationEntries.any { annotation ->
      annotation.shortName?.asString() == "Composable" ||
        annotation.text?.contains("Composable") == true
    }
  if (!hasComposableAnnotation) return false
  // Check if the type is a function type (using the KtFunctionType PSI directly)
  val typeElement = typeRef.typeElement
  return typeElement is org.jetbrains.kotlin.psi.KtFunctionType
}

val KtCallableDeclaration.isModifierReceiver: Boolean
  get() = ModifierNames.contains(receiverTypeReference?.text)

val KtFunction.modifierParameter: KtParameter?
  get() {
    val modifiers = valueParameters.filter { it.isModifier }
    return modifiers.firstOrNull { it.name == "modifier" } ?: modifiers.firstOrNull()
  }

fun UMethod.modifierParameter(evaluator: JavaEvaluator): UParameter? {
  val modifiers = uastParameters.filter { it.isModifier(evaluator) }
  return modifiers.firstOrNull { it.name == "modifier" } ?: modifiers.firstOrNull()
}

fun UMethod.slotParameters(evaluator: JavaEvaluator): List<UParameter> =
  uastParameters.filter { it.isSlotParameter(evaluator) }

val KtProperty.declaresCompositionLocal: Boolean
  get() {
    if (isVar || !hasInitializer()) return false

    val initializer = initializer?.unwrapParenthesis() ?: return false

    return initializer is KtCallExpression &&
      initializer.referenceExpression()?.text in CompositionLocalReferenceExpressions
  }

val KtPropertyAccessor.declaresCompositionLocal: Boolean
  get() {
    if (!isGetter) return false
    val body = bodyExpression ?: bodyBlockExpression
    val expression =
      body?.unwrapBlock()?.unwrapReturnExpression()?.unwrapParenthesis() ?: return false

    return expression is KtCallExpression &&
      expression.referenceExpression()?.text in CompositionLocalReferenceExpressions
  }

private val CompositionLocalReferenceExpressions by
  lazy(LazyThreadSafetyMode.NONE) { setOf("staticCompositionLocalOf", "compositionLocalOf") }

val KtCallExpression.isRestartableEffect: Boolean
  get() = RestartableEffects.contains(calleeExpression?.text)

// From https://developer.android.com/jetpack/compose/side-effects#restarting-effects
// Also includes Circuit's produceRetainedState
private val RestartableEffects by
  lazy(LazyThreadSafetyMode.NONE) {
    setOf("LaunchedEffect", "produceState", "produceRetainedState", "DisposableEffect")
  }
