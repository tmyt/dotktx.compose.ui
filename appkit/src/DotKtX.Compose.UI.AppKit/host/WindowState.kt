package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import AppKit.NSScreen
import AppKit.NSWindow
import CoreGraphics.CGPoint
import CoreGraphics.CGSize

/** Where a window is placed on the screen. */
sealed class WindowPosition {
    /** Wherever AppKit puts a new window (centered on the main screen when it opens). */
    object PlatformDefault : WindowPosition()

    /** Centered on the main screen. */
    object Center : WindowPosition()

    /** Top-left corner at ([x], [y]) in points, measured from the top-left corner of the main screen (y grows downward). */
    data class Absolute(val x: Double, val y: Double) : WindowPosition()
}

/** Whether a window is a normal resizable window or zoomed to fill the screen. */
enum class WindowPlacement { Floating, Maximized }

/**
 * Size, position and placement of a [Window], shared between the application and the user.
 *
 * Changing a property moves or resizes the window. Moving, resizing or zooming the window by hand
 * updates the properties, so they always describe the window. [width] and [height] are the size of
 * the window's content area in points and keep the restored size while the window is zoomed. After
 * the user moves the window, [position] is [WindowPosition.Absolute].
 */
class WindowState(
    width: Double,
    height: Double,
    position: WindowPosition,
    placement: WindowPlacement,
) {
    var width: Double by mutableDoubleStateOf(width)
    var height: Double by mutableDoubleStateOf(height)
    var position: WindowPosition by mutableStateOf(position)
    var placement: WindowPlacement by mutableStateOf(placement)
}

/** Creates a [WindowState] that survives recomposition. */
@Composable
fun rememberWindowState(
    width: Double = 800.0,
    height: Double = 600.0,
    position: WindowPosition = WindowPosition.PlatformDefault,
    placement: WindowPlacement = WindowPlacement.Floating,
): WindowState = remember { WindowState(width, height, position, placement) }

/** Keeps an AppKit window and a [WindowState] in step, in both directions. */
internal class WindowStateSync(private val window: NSWindow, private val state: WindowState) {
    private var subscriptions: List<AutoCloseable> = emptyList()
    private var applying = false
    private var placed = false
    private var lastCentered: CGPoint? = null

    fun start() {
        subscriptions = listOf(
            window.DidResize.subscribe { _, _ -> onChanged() },
            window.DidMove.subscribe { _, _ -> onChanged() },
            window.DidEndLiveResize.subscribe { _, _ -> onChanged() },
        )
    }

    fun stop() {
        for (subscription in subscriptions) subscription.close()
        subscriptions = emptyList()
    }

    /** Moves the window to match the given state; values that already match are left alone. */
    fun apply(width: Double, height: Double, position: WindowPosition, placement: WindowPlacement) {
        applying = true
        try {
            applyState(width, height, position, placement)
        } finally {
            applying = false
        }
    }

    private fun applyState(width: Double, height: Double, position: WindowPosition, placement: WindowPlacement) {
        if (placement == WindowPlacement.Maximized) {
            if (!window.IsZoomed) window.Zoom(window)
            return
        }
        if (window.IsZoomed) window.Zoom(window)
        val size = contentSize()
        val resized = abs(size.Width.Value - width) > 1 || abs(size.Height.Value - height) > 1
        if (resized) window.SetContentSize(CGSize(width, height))
        when (position) {
            WindowPosition.PlatformDefault -> if (!placed) centerWindow()
            WindowPosition.Center -> if (resized || lastCentered == null || !originMatches(lastCentered)) centerWindow()
            is WindowPosition.Absolute -> moveTo(position.x, position.y)
        }
        placed = true
    }

    private fun centerWindow() {
        window.Center()
        lastCentered = window.Frame.Location
    }

    private fun originMatches(origin: CGPoint?): Boolean {
        if (origin == null) return false
        val current = window.Frame.Location
        return abs(current.X.Value - origin.X.Value) <= 1 && abs(current.Y.Value - origin.Y.Value) <= 1
    }

    /** Moves the window so that its top-left corner is at ([x], [y]) in top-left screen coordinates. */
    private fun moveTo(x: Double, y: Double) {
        val frame = window.Frame
        val appKitY = screenHeight() - (y + frame.Height.Value)
        if (abs(frame.X.Value - x) > 1 || abs(frame.Y.Value - appKitY) > 1) window.SetFrameOrigin(CGPoint(x, appKitY))
    }

    /** Reports a change made outside the state (by the user or by AppKit) back to the state. */
    private fun onChanged() {
        if (applying) return
        if (window.IsZoomed) {
            state.placement = WindowPlacement.Maximized
            return
        }
        if (window.IsMiniaturized) return
        state.placement = WindowPlacement.Floating
        val size = contentSize()
        if (abs(state.width - size.Width.Value) > 1) state.width = size.Width.Value
        if (abs(state.height - size.Height.Value) > 1) state.height = size.Height.Value
        val frame = window.Frame
        val x = frame.X.Value
        val y = screenHeight() - (frame.Y.Value + frame.Height.Value)
        val unchanged = when (val current = state.position) {
            WindowPosition.PlatformDefault -> false
            WindowPosition.Center -> originMatches(lastCentered)
            is WindowPosition.Absolute -> abs(current.x - x) <= 1 && abs(current.y - y) <= 1
        }
        if (!unchanged) state.position = WindowPosition.Absolute(x, y)
    }

    private fun contentSize(): CGSize = window.ContentRectFor(window.Frame).Size

    private fun screenHeight(): Double = NSScreen.MainScreen.Frame.Height.Value
}
