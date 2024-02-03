// Copyright (C) 2022 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.BooleanOption
import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity.ERROR
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.StringOption
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiNamedElement
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import slack.lint.compose.util.OptionLoadingDetector
import slack.lint.compose.util.Priorities.NORMAL
import slack.lint.compose.util.StringSetLintOption
import slack.lint.compose.util.sourceImplementation

internal class M2ApiDetector
@JvmOverloads
constructor(
  private val allowList: StringSetLintOption = StringSetLintOption(ALLOW_LIST),
  private val workAroundMangling: BooleanOption = MANGLING_WORKAROUND,
) : OptionLoadingDetector(allowList), SourceCodeScanner {

  companion object {
    private const val M2Package = "androidx.compose.material"

    internal val ALLOW_LIST =
      StringOption(
        "allowed-m2-apis",
        "A comma-separated list of APIs in androidx.compose.material that should be allowed.",
        null,
        "This property should define a comma-separated list of APIs in androidx.compose.material that should be allowed.",
      )

    internal val MANGLING_WORKAROUND =
      BooleanOption(
        "enable-mangling-workaround",
        "Try to work around name mangling.",
        false,
        "See https://github.com/slackhq/compose-lints/issues/167",
      )

    val ISSUE =
      Issue.create(
          id = "ComposeM2Api",
          briefDescription = "Using a Compose M2 API is not recommended",
          explanation =
            """
              Compose Material 2 (M2) is succeeded by Material 3 (M3). Please use M3 APIs.\
              See https://slackhq.github.io/compose-lints/rules/#use-material-3 for more information.
            """,
          category = CORRECTNESS,
          priority = NORMAL,
          severity = ERROR,
          implementation = sourceImplementation<M2ApiDetector>(),
        )
        .setOptions(listOf(ALLOW_LIST, MANGLING_WORKAROUND))
        .setEnabledByDefault(false)
  }

  override fun getApplicableUastTypes() =
    listOf<Class<out UElement>>(
      UCallExpression::class.java,
      UQualifiedReferenceExpression::class.java,
    )

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    // Only applicable to Kotlin files
    if (!isKotlin(context.uastFile?.lang)) return null
    return object : UElementHandler() {
      override fun visitCallExpression(node: UCallExpression) = checkNode(node)

      override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
        val parent = node.uastParent
        if (parent is UQualifiedReferenceExpression && node == parent.receiver) {
          // This is part of a longer selector expression, so let the lint handle the final
          // reference
          // i.e. given 'androidx.compose.material.BottomNavigationDefaults.Elevation', we only want
          // to report 'androidx.compose.material.BottomNavigationDefaults.Elevation', and _not_
          // 'androidx.compose.material.BottomNavigationDefaults'
          return
        }
        return checkNode(node)
      }

      private fun checkNode(node: UResolvable) {
        val resolved = node.resolve() ?: return
        val packageName = context.evaluator.getPackage(resolved)?.qualifiedName ?: return
        if (packageName == M2Package) {
          // Ignore any in the allow-list.
          val resolvedName =
            (resolved as? PsiNamedElement)?.name?.let {
              if (workAroundMangling.getValue(context)) {
                it.substringBefore("-")
              } else {
                it
              }
            }
          if (resolvedName in allowList.value) return
          context.report(
            issue = ISSUE,
            location = context.getLocation(node),
            message = ISSUE.getExplanation(TextFormat.TEXT),
          )
        }
      }
    }
  }
}
