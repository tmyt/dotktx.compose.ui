package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import AppKit.NSColor
import System.Runtime.InteropServices.NFloat

/**
 * Draws a background and an outline around one child (a layer-backed view). [content] must emit at
 * most one element, which fills the box. A `null` color clears it. Space between the outline and the
 * child is [Modifier.padding].
 *
 * ```
 * Box(background = NSColor.Red, cornerRadius = 4.0) { Label("Warning") }
 * ```
 */
@Composable
fun Box(
    modifier: Modifier = Modifier,
    background: NSColor? = null,
    borderColor: NSColor? = null,
    borderWidth: Double = 0.0,
    cornerRadius: Double = 0.0,
    content: @Composable () -> Unit,
) {
    Container({
        val host = ContentHost()
        host.WantsLayer = true
        ElementNode(host, ChildSlot.Single("Box", host))
    }, modifier, {
        set(background) { view.Layer?.BackgroundColor = it?.CGColor }
        set(borderColor) { view.Layer?.BorderColor = it?.CGColor }
        set(borderWidth) { view.Layer?.BorderWidth = NFloat(it) }
        set(cornerRadius) { view.Layer?.CornerRadius = NFloat(it) }
    }, content)
}
