package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import Microsoft.UI.Xaml.Window as XamlWindow

/** The WinUI window that hosts the current composition. */
val LocalWindow = staticCompositionLocalOf<XamlWindow> { error("No window: LocalWindow is only available inside Window { }") }

/** Receiver of [Window]'s content. */
interface WindowScope {
    val window: XamlWindow
}

/**
 * A top-level window that exists while this composable is in the composition.
 *
 * The window's close button does not close it: it calls [onCloseRequest], and the window closes when
 * the application removes it from the composition (for example `onCloseRequest = ::exitApplication`,
 * or a state flag around `Window { }`). [state] holds the window's size, position and placement; see
 * [WindowState].
 */
@Composable
fun ApplicationScope.Window(
    onCloseRequest: () -> Unit,
    title: String = "",
    state: WindowState = rememberWindowState(),
    content: @Composable WindowScope.() -> Unit,
) {
    val host = this as ApplicationHost
    val parent = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val window = remember { XamlWindow() }

    val sync = remember(state) { WindowStateSync(window, state) }
    // Read in composition so that a change to the state recomposes this function and reaches the window.
    val width = state.width
    val height = state.height
    val position = state.position
    val placement = state.placement

    SideEffect {
        window.Title = title
        sync.apply(width, height, position, placement)
    }

    DisposableEffect(window) {
        var closingFromComposition = false
        val closing = window.AppWindow.Closing.subscribe { _, e ->
            if (!closingFromComposition) {
                e.Cancel = true
                currentOnCloseRequest()
            }
        }
        val scope = WindowScopeImpl(window)
        val composition = Composition(WinUIApplier(WindowRoot(window)), parent)
        composition.setContent {
            CompositionLocalProvider(
                LocalWindow provides window,
                LocalXamlRootSource provides { window.Content as? Microsoft.UI.Xaml.FrameworkElement },
            ) { currentContent(scope) }
        }
        host.windowOpened()
        // Size and place the window before it is shown, so it does not open at the default size first.
        sync.apply(state.width, state.height, state.position, state.placement)
        sync.start()
        window.Activate()
        onDispose {
            sync.stop()
            composition.dispose()
            closing.close()
            closingFromComposition = true
            window.Close()
            host.windowClosed()
        }
    }
}

private class WindowScopeImpl(override val window: XamlWindow) : WindowScope

/** Root node of a window's composition; its single child becomes `Window.Content`. */
private class WindowRoot(window: XamlWindow) : Node(ChildSlot.Single("Window") { window.Content = it })
