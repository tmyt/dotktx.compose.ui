package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import dotktx.compose.ui.annotations.AtMostOneElementContent
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.CornerRadius
import Microsoft.UI.Xaml.Thickness
import Microsoft.UI.Xaml.Media.Brush
import Microsoft.UI.Xaml.Controls.Border as XamlBorder

/**
 * Draws a background and an outline around one child (`Border`). [content] must emit at most one
 * element. A `null` brush keeps the value from the theme (none by default). Space between the outline
 * and the child is [Modifier.padding].
 */
@EmitsOneElement
@Composable
fun Border(
    modifier: Modifier = Modifier,
    background: Brush? = null,
    borderBrush: Brush? = null,
    borderThickness: Double = 0.0,
    cornerRadius: Double = 0.0,
    @AtMostOneElementContent content: @Composable () -> Unit,
) {
    Container({ borderNode(XamlBorder()) }, modifier, {
        set(background) { if (it == null) element.ClearValue(XamlBorder.BackgroundProperty) else element.Background = it }
        set(borderBrush) { if (it == null) element.ClearValue(XamlBorder.BorderBrushProperty) else element.BorderBrush = it }
        set(borderThickness) { element.BorderThickness = Thickness(it, it, it, it) }
        set(cornerRadius) { element.CornerRadius = CornerRadius(it, it, it, it) }
    }, content)
}

private fun borderNode(border: XamlBorder): ElementNode<XamlBorder> =
    ElementNode(border, ChildSlot.Single("Border") { border.Child = it })
