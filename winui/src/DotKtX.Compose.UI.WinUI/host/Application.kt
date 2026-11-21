package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import Microsoft.UI.Dispatching.DispatcherQueue
import Microsoft.UI.Xaml.Application
import Microsoft.UI.Xaml.LaunchActivatedEventArgs
import Microsoft.UI.Xaml.Controls.XamlControlsResources
import Microsoft.UI.Xaml.Markup.IXamlMetadataProvider
import Microsoft.UI.Xaml.Markup.IXamlType
import Microsoft.UI.Xaml.Markup.XmlnsDefinition
import Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider
import System.Threading.ApartmentState
import System.Threading.Thread
import System.Threading.ThreadStart
import System.Type

/** The UI thread's `DispatcherQueue`, for code that schedules work outside coroutines. */
val LocalDispatcherQueue = staticCompositionLocalOf<DispatcherQueue> { error("No DispatcherQueue: LocalDispatcherQueue is only available inside application { }") }

/** Receiver of [application]'s content. */
interface ApplicationScope {
    /** Closes every window and ends the application. */
    fun exitApplication()
}

/**
 * Runs a WinUI application whose windows are declared by [content]. Returns when the application
 * exits: after [ApplicationScope.exitApplication], or when [content] no longer contains a window.
 *
 * ```
 * fun main() = application {
 *     Window(onCloseRequest = ::exitApplication, title = "Hello") { TextBlock("Hello") }
 * }
 * ```
 */
fun application(content: @Composable ApplicationScope.() -> Unit) {
    // WinUI needs an STA thread, and DotKt cannot mark `main` with [STAThread].
    val ui = Thread(ThreadStart {
        // Unpackaged apps load the Windows App SDK runtime before the first WinUI call.
        bootstrapWindowsAppSDK()
        Application.Start { _ -> ComposeApplication(content) }
        shutdownWindowsAppSDK()
    })
    ui.SetApartmentState(ApartmentState.STA)
    ui.Start()
    ui.Join()
}

/**
 * The WinUI `Application` of a Compose app.
 *
 * Code-only apps have no `App.xaml`, so two things that the XAML compiler normally provides are set
 * up here: XAML type metadata (delegated to `XamlControlsXamlMetaDataProvider`) and the Fluent theme
 * resources (`XamlControlsResources`). Without them, control templates fail to resolve their theme
 * resources.
 */
internal class ComposeApplication(
    private val content: @Composable ApplicationScope.() -> Unit,
) : Application(), IXamlMetadataProvider {
    private val xamlMetadata = XamlControlsXamlMetaDataProvider()
    private var host: ApplicationHost? = null

    // A provider returns null for types it does not own, so that XAML can ask the next provider.
    override fun GetXamlType(type: Type): IXamlType? = xamlMetadata.GetXamlType(type)
    override fun GetXamlType(fullName: String): IXamlType? = xamlMetadata.GetXamlType(fullName)
    override fun GetXmlnsDefinitions(): Array<XmlnsDefinition> = xamlMetadata.GetXmlnsDefinitions()

    override fun OnLaunched(args: LaunchActivatedEventArgs) {
        // Exceptions escaping OnLaunched are swallowed at the WinRT boundary and the process exits
        // with code 0, so report them before that happens.
        UnhandledException.subscribe { _, e -> println("dotktx.compose.ui.winui: unhandled exception: " + e.Message) }
        Resources.MergedDictionaries.Add(XamlControlsResources())
        host = ApplicationHost(UIRuntime.forCurrentThread()).also { it.start(content) }
    }
}

/**
 * Owns the application-level composition, on the UI thread's [UIRuntime]. Windows are composed as
 * effects of this composition and count themselves in and out through [windowOpened] and
 * [windowClosed].
 */
internal class ApplicationHost(private val runtime: UIRuntime) : ApplicationScope {
    private val queue = runtime.queue
    private var composition: Composition? = null
    private var windowCount = 0
    private var exiting = false

    fun start(content: @Composable ApplicationScope.() -> Unit) {
        val root = Composition(ApplicationApplier(), runtime.recomposer)
        composition = root
        root.setContent { CompositionLocalProvider(LocalDispatcherQueue provides queue) { content() } }
    }

    internal fun windowOpened() {
        windowCount++
    }

    internal fun windowClosed() {
        windowCount--
        if (windowCount == 0) exitApplication()
    }

    override fun exitApplication() {
        if (exiting) return
        exiting = true
        // Deferred to its own queue turn: this may be called while the composition applies changes,
        // and a composition cannot be disposed from inside its own apply phase.
        queue.TryEnqueue {
            composition?.dispose()
            composition = null
            runtime.shutdown()
            Application.Current.Exit()
        }
    }
}
