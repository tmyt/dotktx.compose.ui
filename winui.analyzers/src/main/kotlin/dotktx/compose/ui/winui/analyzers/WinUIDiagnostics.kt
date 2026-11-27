package dotktx.compose.ui.winui.analyzers

import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.warning0

/**
 * Diagnostics reported by the WinUI analyzers. A property name is also the name that
 * `@Suppress("...")` takes.
 *
 * The PSI type is `KtElement` rather than `PsiElement`: kotc's embeddable compiler relocates
 * IntelliJ classes under `org.jetbrains.kotlin.com.intellij`, the Kotlin LSP does not, and
 * `org.jetbrains.kotlin.psi.KtElement` has the same name in both, so one jar links in both.
 */
object WinUIDiagnostics : KtDiagnosticsContainer() {
    val MAIN_WITHOUT_STA_THREAD by warning0<KtElement>(SourceElementPositioningStrategies.REFERENCED_NAME_BY_QUALIFIED)

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = WinUIDiagnosticMessages
}

internal object WinUIDiagnosticMessages : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("WinUI") { map ->
        map.put(
            WinUIDiagnostics.MAIN_WITHOUT_STA_THREAD,
            "mark main with @STAThreadAttribute (import System.STAThreadAttribute) so that WinUI runs on the main thread. " +
                "Without it, application { } starts a dedicated STA thread and waits for it.",
        )
    }
}
