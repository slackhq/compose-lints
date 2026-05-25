// Copyright (C) 2024 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose.util

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.model.LintModelDependencies
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiType
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull
import kotlin.metadata.ClassKind
import kotlin.metadata.KmClass
import kotlin.metadata.Modality
import kotlin.metadata.isData
import kotlin.metadata.isValue
import kotlin.metadata.jvm.JvmMetadataVersion
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.Metadata as MetadataWithNullableArgs
import kotlin.metadata.kind
import kotlin.metadata.modality
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.findContaining
import org.jetbrains.uast.toUElementOfType

/**
 * A delegating [JavaEvaluator] that implements more comprehensive checks for Kotlin classes via
 * metadata annotations.
 *
 * This is important because, when `checkDependencies` is set to false, Lint detectors cannot see
 * Kotlin language features in externally-compiled elements. This means that constructs like `data
 * classes` or `value classes` are not visible. Using kotlin-metadata, we can parse the [Metadata]
 * annotations on the containing classes and read these language features from them.
 *
 * This is necessary due to https://issuetracker.google.com/issues/283654244.
 *
 * Ported from
 * [slack-lints](https://github.com/slackhq/slack-lints/blob/main/slack-lint-checks/src/main/java/slack/lint/util/MetadataJavaEvaluator.kt).
 */
class MetadataJavaEvaluator(private val file: String, private val delegate: JavaEvaluator) :
  JavaEvaluator() {

  private companion object {
    // Not an exhaustive list, but at least the ones we look at currently
    private val KOTLIN_METADATA_TOKENS =
      mapOf(
        KtTokens.DATA_KEYWORD to TokenData { it.isData },
        KtTokens.SEALED_KEYWORD to
          TokenData(applicableClassKinds = setOf(JvmClassKind.CLASS, JvmClassKind.INTERFACE)) {
            it.modality == Modality.SEALED
          },
        KtTokens.OBJECT_KEYWORD to TokenData { it.kind == ClassKind.OBJECT },
        KtTokens.COMPANION_KEYWORD to TokenData { it.kind == ClassKind.COMPANION_OBJECT },
        KtTokens.VALUE_KEYWORD to TokenData { it.isValue },
      )
  }

  private data class TokenData(
    val applicableClassKinds: Set<JvmClassKind> = setOf(JvmClassKind.CLASS),
    val isApplicable: (KmClass) -> Boolean,
  )

  /** Flag to disable as needed. */
  private val checkMetadata =
    System.getProperty("slack.lint.compose.checkMetadata", "true").toBoolean()
  private val cachedClasses = ConcurrentHashMap<String, Optional<KmClass>>()

  // region Delegating functions
  override val dependencies: LintModelDependencies?
    get() = delegate.dependencies

  override fun extendsClass(cls: PsiClass?, className: String, strict: Boolean): Boolean =
    delegate.extendsClass(cls, className, strict)

  @Suppress("DEPRECATION")
  @Deprecated(
    "Use getAnnotation returning a UAnnotation instead",
    replaceWith = ReplaceWith("getAnnotation(listOwner, *annotationNames)"),
  )
  override fun findAnnotation(
    listOwner: PsiModifierListOwner?,
    vararg annotationNames: String,
  ): PsiAnnotation? = delegate.findAnnotation(listOwner, *annotationNames)

  @Suppress("DEPRECATION")
  @Deprecated(
    "Use getAnnotationInHierarchy returning a UAnnotation instead",
    replaceWith = ReplaceWith("getAnnotationInHierarchy(listOwner, *annotationNames)"),
  )
  override fun findAnnotationInHierarchy(
    listOwner: PsiModifierListOwner,
    vararg annotationNames: String,
  ): PsiAnnotation? = delegate.findAnnotationInHierarchy(listOwner, *annotationNames)

  override fun findClass(qualifiedName: String): PsiClass? = delegate.findClass(qualifiedName)

  override fun findJarPath(element: PsiElement): String? = delegate.findJarPath(element)

  override fun findJarPath(element: UElement): String? = delegate.findJarPath(element)

  @Suppress("DEPRECATION")
  @Deprecated(
    "Use getAnnotations() instead; consider providing a parent",
    replaceWith = ReplaceWith("getAnnotations(owner, inHierarchy)"),
  )
  override fun getAllAnnotations(
    owner: PsiModifierListOwner,
    inHierarchy: Boolean,
  ): Array<PsiAnnotation> = delegate.getAllAnnotations(owner, inHierarchy)

  override fun getAllAnnotations(owner: UAnnotated, inHierarchy: Boolean): List<UAnnotation> =
    delegate.getAllAnnotations(owner, inHierarchy)

  override fun getAnnotation(
    listOwner: PsiModifierListOwner?,
    vararg annotationNames: String,
  ): UAnnotation? = delegate.getAnnotation(listOwner, *annotationNames)

  override fun getAnnotationInHierarchy(
    listOwner: PsiModifierListOwner,
    vararg annotationNames: String,
  ): UAnnotation? = delegate.getAnnotationInHierarchy(listOwner, *annotationNames)

  override fun getAnnotations(
    owner: PsiModifierListOwner?,
    inHierarchy: Boolean,
    parent: UElement?,
  ): List<UAnnotation> = delegate.getAnnotations(owner, inHierarchy, parent)

  override fun getClassType(psiClass: PsiClass?): PsiClassType? = delegate.getClassType(psiClass)

  override fun getPackage(node: PsiElement): PsiPackage? = delegate.getPackage(node)

  override fun getPackage(node: UElement): PsiPackage? = delegate.getPackage(node)

  override fun getTypeClass(psiType: PsiType?): PsiClass? = delegate.getTypeClass(psiType)

  override fun implementsInterface(cls: PsiClass, interfaceName: String, strict: Boolean): Boolean =
    delegate.implementsInterface(cls, interfaceName, strict)

  // endregion

  /** Deep isObject check that checks if the given [cls] is an `object` class. */
  fun isObject(cls: PsiClass?): Boolean {
    if (cls == null) return false

    (cls as? UClass ?: cls.toUElementOfType<UClass>())?.let { uClass ->
      if (uClass.sourcePsi is KtObjectDeclaration) {
        return true
      } else if (canCheckMetadata(cls)) {
        val (applicableClassKinds, isApplicable) =
          KOTLIN_METADATA_TOKENS.getValue(KtTokens.OBJECT_KEYWORD)
        if (uClass.javaPsi.classKind in applicableClassKinds) {
          uClass.getOrParseMetadata()?.let { kmClass ->
            return isApplicable(kmClass)
          }
        }
      }
    }
    return false
  }

  private fun canCheckMetadata(element: PsiElement): Boolean {
    return checkMetadata && element is PsiCompiledElement
  }

  override fun hasModifier(owner: PsiModifierListOwner?, keyword: KtModifierKeywordToken): Boolean {
    val superValue = super.hasModifier(owner, keyword)
    // If it's not a compiled element or not a PsiClass, trust the super value and move on
    if (owner !is PsiClass || !canCheckMetadata(owner)) {
      return superValue
    }

    // We're working with an externally compiled element and it's a PsiClass, so we can do more
    // thorough checks here.
    KOTLIN_METADATA_TOKENS[keyword]?.let { (applicableClassKinds, isApplicable) ->
      owner.findContaining(UClass::class.java)?.let { cls ->
        // Only parse if the target class kind is applicable to the token we're checking. For
        // example - when checking `data` tokens, they're not applicable to interfaces or enums.
        if (cls.javaPsi.classKind in applicableClassKinds) {
          cls.getOrParseMetadata()?.let { kmClass ->
            return isApplicable(kmClass)
          }
        }
      }
    }

    return superValue
  }

  private fun UAnnotated.getOrParseMetadata(): KmClass? {
    val cls =
      when (this) {
        is UClass -> this
        else -> return null // Only classes are supported right now
      }
    val fqcn =
      qualifiedName
        ?: run {
          metadataErrorLog("Qualified name is null for $cls in file $file")
          return null
        }
    return cachedClasses
      // Don't use getOrPut. Kotlin's extension may still invoke the body and we don't want that
      .computeIfAbsent(fqcn) { key ->
        val annotation =
          cls.findAnnotation("kotlin.Metadata") ?: return@computeIfAbsent Optional.empty()
        val (durationMillis, metadata) =
          measureTimeMillisWithResult { annotation.parseMetadata(key) }
        metadataLog("Took ${durationMillis}ms to parse metadata for $key.")
        Optional.ofNullable(metadata)
      }
      .getOrNull()
  }

  private fun UAnnotation.parseMetadata(classNameHint: String): KmClass? {
    val parsedMetadata =
      try {
        KotlinClassMetadata.readStrict(toMetadataAnnotation())
      } catch (e: IllegalArgumentException) {
        try {
          KotlinClassMetadata.readLenient(toMetadataAnnotation()).also {
            metadataErrorLog(
              "Could not load metadata for $classNameHint from file $file with strict parsing. Using lenient parsing."
            )
          }
        } catch (e: IllegalArgumentException) {
          // Extremely weird case, log this specifically
          metadataErrorLog(
            "Could not load metadata for $classNameHint from file $file. This usually happens if the Kotlin version the class was compiled against is too new for lint to read (${JvmMetadataVersion.LATEST_STABLE_SUPPORTED})."
          )
          return null
        }
      }
    return when (parsedMetadata) {
      is KotlinClassMetadata.Class -> {
        parsedMetadata.kmClass.also {
          metadataLog("Loaded KmClass for $classNameHint from file $file")
        }
      }
      else -> {
        metadataLog(
          """
            Could not load KmClass for $classNameHint from file $file.
            Metadata was $parsedMetadata
          """
            .trimIndent()
        )
        null
      }
    }
  }

  private fun UAnnotation.toMetadataAnnotation(): Metadata {
    return MetadataWithNullableArgs(
      kind = findAttributeValue("k")?.parseIntMember(),
      metadataVersion = findAttributeValue("mv")?.parseIntArray(),
      data1 = findAttributeValue("d1")?.parseStringArray(),
      data2 = findAttributeValue("d2")?.parseStringArray(),
      extraString = findAttributeValue("xs")?.parseStringMember(),
      packageName = findAttributeValue("pn")?.parseStringMember(),
      extraInt = findAttributeValue("xi")?.parseIntMember(),
    )
  }

  private val PsiLiteralExpression.intValue: Int
    get() = stringValue.toInt()

  private val PsiLiteralExpression.stringValue: String
    get() = value.toString()

  private fun UExpression.parseIntMember() = (sourcePsi as PsiLiteralExpression).intValue

  private fun UExpression.parseStringMember() = (sourcePsi as PsiLiteralExpression).stringValue

  private fun UExpression.parseStringArray() =
    (sourcePsi as PsiArrayInitializerMemberValue).initializers.mapArray { value ->
      (value as PsiLiteralExpression).stringValue
    }

  private fun UExpression.parseIntArray(): IntArray {
    val initializers = (sourcePsi as PsiArrayInitializerMemberValue).initializers
    return IntArray(initializers.size) { index ->
      (initializers[index] as PsiLiteralExpression).intValue
    }
  }
}

private inline fun <T, reified R> Array<out T>.mapArray(transform: (T) -> R): Array<R> =
  Array(this.size) { i -> transform(this[i]) }

private inline fun <T> measureTimeMillisWithResult(block: () -> T): Pair<Long, T> {
  val start = System.currentTimeMillis()
  val result = block()
  return Pair(System.currentTimeMillis() - start, result)
}

private val logVerbosely by lazy {
  System.getProperty("slack.lint.compose.logVerbosely", "false").toBoolean()
}
private val logErrorsVerbosely by lazy {
  System.getProperty("slack.lint.compose.logErrorsVerbosely", "true").toBoolean()
}

/** Logs to std if `slack.lint.compose.logVerbosely` is enabled. Useful for debugging. */
private fun metadataLog(message: String) {
  if (logVerbosely) {
    println("ComposeLint: $message")
  }
}

/**
 * Logs to std err if `slack.lint.compose.logErrorsVerbosely` is enabled. Important for errors that
 * you don't necessarily want to fail the build.
 */
private fun metadataErrorLog(message: String) {
  if (logErrorsVerbosely) {
    System.err.println("ComposeLint: $message")
  }
}
