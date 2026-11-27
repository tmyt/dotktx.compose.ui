package dotktx.compose.ui.analyzers

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.diagnostics.warning1
import org.jetbrains.kotlin.psi.KtElement

/**
 * Diagnostics reported by the analyzers. A property name is also the name that `@Suppress("...")` takes.
 *
 * The PSI type is `KtElement` rather than `PsiElement`: kotc's embeddable compiler relocates
 * IntelliJ classes under `org.jetbrains.kotlin.com.intellij`, the Kotlin LSP does not, and
 * `org.jetbrains.kotlin.psi.KtElement` has the same name in both, so one jar links in both.
 */
object AnalyzerDiagnostics : KtDiagnosticsContainer() {
    /** A function marked `@RequiresSTAThread` called from a top-level `main` without `@STAThreadAttribute`. */
    val MAIN_WITHOUT_STA_THREAD by warning1<KtElement, String>(SourceElementPositioningStrategies.REFERENCED_NAME_BY_QUALIFIED)

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = AnalyzerDiagnosticMessages
}

internal object AnalyzerDiagnosticMessages : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("DotKtX.Compose.UI") { map ->
        map.put(
            AnalyzerDiagnostics.MAIN_WITHOUT_STA_THREAD,
            "{0} needs an STA main thread; mark main with @STAThreadAttribute (import System.STAThreadAttribute).",
            CommonRenderers.STRING,
        )
    }
}
