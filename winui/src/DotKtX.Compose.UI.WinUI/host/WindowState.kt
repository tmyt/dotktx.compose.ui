package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt
import Microsoft.UI.Windowing.AppWindowChangedEventArgs
import Microsoft.UI.Windowing.DisplayArea
import Microsoft.UI.Windowing.DisplayAreaFallback
import Microsoft.UI.Windowing.OverlappedPresenter
import Microsoft.UI.Windowing.OverlappedPresenterState
import Microsoft.UI.Xaml.FrameworkElement
import Windows.Graphics.PointInt32
import Windows.Graphics.SizeInt32
import Microsoft.UI.Xaml.Window as XamlWindow

/** Where a window is placed on the screen. */
sealed class WindowPosition {
    /** Wherever Windows puts a new window. */
    object PlatformDefault : WindowPosition()

    /** Centered in the work area of the display the window opens on. */
    object Center : WindowPosition()

    /** Top-left corner at ([x], [y]), in effective pixels of the window's display. */
    data class Absolute(val x: Double, val y: Double) : WindowPosition()
}

/** Whether a window is a normal resizable window or fills the display's work area. */
enum class WindowPlacement { Floating, Maximized }

/**
 * Size, position and placement of a [Window], shared between the application and the user.
 *
 * Changing a property moves or resizes the window. Moving, resizing or maximizing the window by hand
 * updates the properties, so they always describe the window. [width] and [height] are the outer
 * size in effective pixels (scaled with the display's DPI) and keep the restored size while the window
 * is maximized. After the user moves the window, [position] is [WindowPosition.Absolute].
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

/** Keeps a WinUI window and a [WindowState] in step, in both directions. */
internal class WindowStateSync(private val window: XamlWindow, private val state: WindowState) {
    private val appWindow = window.AppWindow
    private var changed: AutoCloseable? = null
    private var loaded: AutoCloseable? = null

    fun start() {
        changed = appWindow.Changed.subscribe { _, e -> onChanged(e) }
        // Effective pixels need the display scale, which the window content knows once it is loaded.
        val content = window.Content as? FrameworkElement
        if (content != null && content.XamlRoot == null) {
            loaded = content.Loaded.subscribe { _, _ ->
                loaded?.close()
                loaded = null
                apply(state.width, state.height, state.position, state.placement)
            }
        }
    }

    fun stop() {
        changed?.close()
        changed = null
        loaded?.close()
        loaded = null
    }

    /** Moves the window to match the given state; values that already match are left alone. */
    fun apply(width: Double, height: Double, position: WindowPosition, placement: WindowPlacement) {
        val presenter = appWindow.Presenter as? OverlappedPresenter
        if (placement == WindowPlacement.Maximized) {
            if (presenter != null && presenter.State != OverlappedPresenterState.Maximized) presenter.Maximize()
            return
        }
        if (presenter != null && presenter.State == OverlappedPresenterState.Maximized) presenter.Restore()
        val scale = scale()
        val w = (width * scale).roundToInt()
        val h = (height * scale).roundToInt()
        val size = appWindow.Size
        if (abs(size.Width - w) > 1 || abs(size.Height - h) > 1) appWindow.Resize(SizeInt32(w, h))
        when (position) {
            WindowPosition.PlatformDefault -> {}
            WindowPosition.Center -> {
                val area = DisplayArea.GetFromWindowId(appWindow.Id, DisplayAreaFallback.Nearest).WorkArea
                moveTo(area.X + (area.Width - w) / 2, area.Y + (area.Height - h) / 2)
            }
            is WindowPosition.Absolute -> moveTo((position.x * scale).roundToInt(), (position.y * scale).roundToInt())
        }
    }

    private fun moveTo(x: Int, y: Int) {
        val current = appWindow.Position
        if (abs(current.X - x) > 1 || abs(current.Y - y) > 1) appWindow.Move(PointInt32(x, y))
    }

    /** Reports a change made outside the state (by the user or by Windows) back to the state. */
    private fun onChanged(e: AppWindowChangedEventArgs) {
        val presenter = appWindow.Presenter as? OverlappedPresenter ?: return
        when (presenter.State) {
            OverlappedPresenterState.Maximized -> {
                state.placement = WindowPlacement.Maximized
                return
            }
            OverlappedPresenterState.Minimized -> return
            else -> state.placement = WindowPlacement.Floating
        }
        val scale = scale()
        if (e.DidSizeChange) {
            val size = appWindow.Size
            // Ignore differences from rounding to whole pixels, so a size the application set stays as written.
            if (abs((state.width * scale).roundToInt() - size.Width) > 1) state.width = size.Width / scale
            if (abs((state.height * scale).roundToInt() - size.Height) > 1) state.height = size.Height / scale
        }
        if (e.DidPositionChange) {
            val point = appWindow.Position
            val unchanged = when (val current = state.position) {
                WindowPosition.PlatformDefault -> false
                WindowPosition.Center -> centering(point)
                is WindowPosition.Absolute ->
                    abs((current.x * scale).roundToInt() - point.X) <= 1 && abs((current.y * scale).roundToInt() - point.Y) <= 1
            }
            if (!unchanged) state.position = WindowPosition.Absolute(point.X / scale, point.Y / scale)
        }
    }

    /** True while the window sits where [WindowPosition.Center] puts it. */
    private fun centering(point: PointInt32): Boolean {
        val size = appWindow.Size
        val area = DisplayArea.GetFromWindowId(appWindow.Id, DisplayAreaFallback.Nearest).WorkArea
        return abs(area.X + (area.Width - size.Width) / 2 - point.X) <= 1 &&
            abs(area.Y + (area.Height - size.Height) / 2 - point.Y) <= 1
    }

    private fun scale(): Double = (window.Content as? FrameworkElement)?.XamlRoot?.RasterizationScale ?: 1.0
}
