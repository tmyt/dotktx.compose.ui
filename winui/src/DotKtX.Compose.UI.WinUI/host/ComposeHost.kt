package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import dotktx.compose.ui.annotations.AtMostOneElementContent
import Microsoft.UI.Xaml.FrameworkElement
import Microsoft.UI.Xaml.Controls.ContentControl

/**
 * The element whose `XamlRoot` popups such as [ContentDialog] attach to: the window content inside
 * [Window], the host itself inside [ComposeHost].
 */
internal val LocalXamlRootSource = staticCompositionLocalOf<() -> FrameworkElement?> { { null } }

/**
 * Hosts Compose content inside an existing WinUI element tree, the counterpart of Android's
 * `ComposeView`. Use it to adopt Compose screen by screen in an app built with XAML or code-behind.
 *
 * The content is composed while the host is loaded (in the visual tree) and disposed when it is
 * unloaded; remembered state does not survive removing the host from the tree. All hosts and
 * [application] windows on a UI thread share one `Recomposer`, so snapshot state written by
 * ordinary WinUI code (an event handler, for example) recomposes them.
 *
 * ```
 * val host = ComposeHost()
 * host.setContent { Counter() }
 * panel.Children.Add(host)
 * ```
 */
open class ComposeHost : ContentControl() {
    private var content: (@Composable () -> Unit)? = null
    private var composition: Composition? = null

    init {
        // Qualified: inside a FrameworkElement subclass the short names resolve to its own properties.
        HorizontalContentAlignment = Microsoft.UI.Xaml.HorizontalAlignment.Stretch
        VerticalContentAlignment = Microsoft.UI.Xaml.VerticalAlignment.Stretch
        Loaded.subscribe { _, _ -> compose() }
        Unloaded.subscribe { _, _ -> disposeComposition() }
    }

    /** Sets the content, replacing the previous one. It is composed now if the host is loaded. */
    fun setContent(@AtMostOneElementContent content: @Composable () -> Unit) {
        this.content = content
        if (IsLoaded) compose()
    }

    /** Disposes the composition now instead of when the host is unloaded. */
    fun disposeComposition() {
        composition?.dispose()
        composition = null
    }

    private fun compose() {
        val body = content ?: return
        val runtime = UIRuntime.forCurrentThread()
        val target = composition ?: Composition(WinUIApplier(hostRoot()), runtime.recomposer).also { composition = it }
        val host = this
        target.setContent {
            CompositionLocalProvider(
                LocalDispatcherQueue provides runtime.queue,
                LocalXamlRootSource provides { host },
            ) { body() }
        }
    }

    private fun hostRoot(): Node {
        val host = this
        return HostRoot(ChildSlot.Single("ComposeHost") { host.Content = it })
    }
}

private class HostRoot(slot: ChildSlot) : Node(slot)
