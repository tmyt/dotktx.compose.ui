package dotktx.compose.ui.winui.analyzers

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

/** Entry point that kotc loads from `-Xplugin`; the analyzers only add FIR checkers. */
class WinUIAnalyzersRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "dotktx.compose.ui.winui.analyzers"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(WinUIFirExtensionRegistrar())
    }
}

internal class WinUIFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::WinUICheckers
        registerDiagnosticContainers(WinUIDiagnostics)
    }
}
