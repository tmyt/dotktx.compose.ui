package dotktx.compose.ui.appkit

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
import dotktx.compose.ui.annotations.AtMostOneElementContent
import AppKit.NSBackingStore
import AppKit.NSWindow
import AppKit.NSWindowStyle
import CoreGraphics.CGRect

/** The AppKit window that hosts the current composition. */
val LocalWindow = staticCompositionLocalOf<NSWindow> { error("No window: LocalWindow is only available inside Window { }") }

/** Receiver of [Window]'s content. */
interface WindowScope {
    val window: NSWindow
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
    @AtMostOneElementContent content: @Composable WindowScope.() -> Unit,
) {
    val appHost = this as ApplicationHost
    val parent = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val window = remember {
        val style = NSWindowStyle.Titled or NSWindowStyle.Closable or NSWindowStyle.Miniaturizable or NSWindowStyle.Resizable
        NSWindow(CGRect(0.0, 0.0, state.width, state.height), style, NSBackingStore.Buffered, false).also {
            // The default (true) frees the native window on close, which leaves the managed wrapper dangling.
            it.ReleasedWhenClosed = false
        }
    }
    val contentHost = remember { ContentHost() }

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
        window.ContentView = contentHost
        window.WindowShouldClose = { _ ->
            currentOnCloseRequest()
            false
        }
        val scope = WindowScopeImpl(window)
        val composition = Composition(AppKitApplier(WindowRoot(contentHost)), parent)
        composition.setContent {
            CompositionLocalProvider(LocalWindow provides window) { currentContent(scope) }
        }
        appHost.windowOpened()
        // Size and place the window before it is shown, so it does not open at the default size first.
        sync.apply(state.width, state.height, state.position, state.placement)
        sync.start()
        window.MakeKeyAndOrderFront(window)
        onDispose {
            sync.stop()
            window.OrderOut(window)
            composition.dispose()
            window.Close()
            appHost.windowClosed()
        }
    }
}

private class WindowScopeImpl(override val window: NSWindow) : WindowScope

/** Root node of a window's composition; its single child becomes the window's content. */
private class WindowRoot(host: ContentHost) : Node(ChildSlot.Single("Window", host))
