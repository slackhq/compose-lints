package slack.lint.compose

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UMethod
import slack.lint.compose.util.Priorities
import slack.lint.compose.util.findChildrenByClass
import slack.lint.compose.util.sourceImplementation

class LocaleGetDefaultDetector : ComposableFunctionDetector(), SourceCodeScanner {

    companion object {
        val ISSUE =
            Issue.create(
                id = "LocaleGetDefaultDetector",
                briefDescription = "Avoid using Locale.getDefault() in Composable functions",
                explanation =
                """
                    Using `Locale.getDefault()` in a @Composable function does not trigger recomposition when the locale changes (e.g., during a Configuration change). \  
                    Instead, use `LocalConfiguration.current.locales`, which correctly updates UI and works better for previews and tests.  
                """,
                category = Category.CORRECTNESS,
                priority = Priorities.NORMAL,
                severity = Severity.ERROR,
                implementation = sourceImplementation<LocaleGetDefaultDetector>(),
            )
    }

    override fun visitComposable(context: JavaContext, method: UMethod, function: KtFunction) {

        function
            .findChildrenByClass<KtProperty>()
            .filter {
                val property = it.initializer?.text ?: return@filter false
                property.contains("Locale.getDefault()")
            }.forEach { property ->
                context.report(
                    ISSUE,
                    property,
                    context.getLocation(property),
                    """
                        Don't use `Locale.getDefault()` in a @Composable function.
                        Use `LocalConfiguration.current.locales` instead to properly handle locale changes."
                     """
                )
            }
    }
}