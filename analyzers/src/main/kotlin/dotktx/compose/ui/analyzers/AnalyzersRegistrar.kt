package dotktx.compose.ui.analyzers

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

/** Entry point that kotc and the Kotlin LSP load from the plugin classpath; the analyzers only add FIR checkers. */
class AnalyzersRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "dotktx.compose.ui.analyzers"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(AnalyzersFirExtensionRegistrar())
    }
}

internal class AnalyzersFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::Checkers
        registerDiagnosticContainers(AnalyzerDiagnostics)
    }
}
