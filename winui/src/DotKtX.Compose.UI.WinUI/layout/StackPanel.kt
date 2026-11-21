package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import Microsoft.UI.Xaml.Controls.Orientation
import Microsoft.UI.Xaml.Controls.StackPanel as XamlStackPanel

/** Arranges its children in one line (`StackPanel`). [spacing] is the gap between children, in effective pixels. */
@Composable
fun StackPanel(
    modifier: Modifier = Modifier,
    orientation: Orientation = Orientation.Vertical,
    spacing: Double = 0.0,
    content: @Composable () -> Unit,
) {
    Container({ panelNode(XamlStackPanel()) }, modifier, {
        set(orientation) { element.Orientation = it }
        set(spacing) { element.Spacing = it }
    }, content)
}

private fun panelNode(panel: XamlStackPanel): ElementNode<XamlStackPanel> =
    ElementNode(panel, ChildSlot.Many(panel.Children))
