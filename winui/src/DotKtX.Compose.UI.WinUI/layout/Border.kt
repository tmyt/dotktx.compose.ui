package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import Microsoft.UI.Xaml.CornerRadius
import Microsoft.UI.Xaml.Thickness
import Microsoft.UI.Xaml.Media.Brush
import Microsoft.UI.Xaml.Controls.Border as XamlBorder

/** Space inside an element's edge, in effective pixels. Start/End follow the element's `FlowDirection`. */
data class Padding(val start: Double, val top: Double, val end: Double, val bottom: Double) {
    constructor(all: Double) : this(all, all, all, all)
    constructor(horizontal: Double, vertical: Double) : this(horizontal, vertical, horizontal, vertical)

    internal fun toThickness(): Thickness = Thickness(start, top, end, bottom)

    companion object {
        val Zero = Padding(0.0)
    }
}

/**
 * Draws a background and an outline around one child (`Border`). [content] must emit at most one
 * element. A `null` brush keeps the value from the theme (none by default).
 */
@Composable
fun Border(
    modifier: Modifier = Modifier,
    padding: Padding = Padding.Zero,
    background: Brush? = null,
    borderBrush: Brush? = null,
    borderThickness: Double = 0.0,
    cornerRadius: Double = 0.0,
    content: @Composable () -> Unit,
) {
    Container({ borderNode(XamlBorder()) }, modifier, {
        set(padding) { element.Padding = it.toThickness() }
        set(background) { if (it == null) element.ClearValue(XamlBorder.BackgroundProperty) else element.Background = it }
        set(borderBrush) { if (it == null) element.ClearValue(XamlBorder.BorderBrushProperty) else element.BorderBrush = it }
        set(borderThickness) { element.BorderThickness = Thickness(it, it, it, it) }
        set(cornerRadius) { element.CornerRadius = CornerRadius(it, it, it, it) }
    }, content)
}

private fun borderNode(border: XamlBorder): ElementNode<XamlBorder> =
    ElementNode(border, ChildSlot.Single("Border") { border.Child = it })
