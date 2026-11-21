package dotktx.compose.ui.winui

import Microsoft.Windows.ApplicationModel.DynamicDependency.Bootstrap
import Microsoft.Windows.ApplicationModel.DynamicDependency.PackageVersion
import System.AppContext

private const val SettingPrefix = "DotKtX.Compose.UI.WinUI.WindowsAppSDK."

/**
 * Loads the Windows App SDK runtime for an unpackaged app; call it on the UI thread before the first
 * WinUI call. [application] calls it; apps that start WinUI themselves (to use [ComposeHost]) call it
 * before `Application.Start`.
 *
 * The runtime is the version the application references (the `Microsoft.WindowsAppSDK` package),
 * written to runtimeconfig.json at build time by this package's `buildTransitive` targets. If no
 * matching runtime is installed, Windows offers to install it and this function throws.
 */
fun bootstrapWindowsAppSDK() {
    val majorMinor = setting("MajorMinor")?.toLongOrNull()
        ?: throw IllegalStateException(
            "The Windows App SDK version is missing from runtimeconfig.json. Reference DotKtX.Compose.UI.WinUI as a " +
                "package, or import its buildTransitive/DotKtX.Compose.UI.WinUI.targets when using a project reference.",
        )
    val versionTag = setting("VersionTag") ?: ""
    val minVersion = setting("MinVersion")?.toULongOrNull() ?: 0uL
    Bootstrap.Initialize(
        majorMinor.toUInt(),
        versionTag,
        PackageVersion(minVersion),
        Bootstrap.InitializeOptions.OnNoMatch_ShowUI,
    )
}

/** Releases the runtime loaded by [bootstrapWindowsAppSDK]; call it after `Application.Start` returns. */
fun shutdownWindowsAppSDK() {
    Bootstrap.Shutdown()
}

private fun setting(name: String): String? = (AppContext.GetData(SettingPrefix + name) as? String)?.takeIf { it.isNotEmpty() }
