package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import Microsoft.UI.Xaml.TextWrapping
import Microsoft.UI.Xaml.Controls.TextBlock as XamlTextBlock

/**
 * Read-only text (`TextBlock`). [fontSize] is in effective pixels; `NaN` keeps the size from the
 * theme. [wrap] breaks lines at the available width instead of clipping.
 */
@Composable
fun TextBlock(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Double = Double.NaN,
    wrap: Boolean = false,
) {
    Leaf({ ElementNode(XamlTextBlock(), ChildSlot.None("TextBlock")) }, modifier) {
        set(text) { element.Text = it }
        set(fontSize) { if (it.isNaN()) element.ClearValue(XamlTextBlock.FontSizeProperty) else element.FontSize = it }
        set(wrap) { element.TextWrapping = if (it) TextWrapping.Wrap else TextWrapping.NoWrap }
    }
}
