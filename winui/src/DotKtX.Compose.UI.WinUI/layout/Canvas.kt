package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import Microsoft.UI.Xaml.Controls.Canvas as XamlCanvas

/**
 * Places its children at fixed coordinates (`Canvas`). Each child sets its position with
 * [Modifier.canvasPosition]; children without one sit at the top-left corner. The canvas does not
 * size itself to its children, so give it a size when its parent does not stretch it.
 *
 * ```
 * Canvas(Modifier.size(300.0, 200.0)) {
 *     TextBlock("Here", Modifier.canvasPosition(left = 40.0, top = 20.0))
 * }
 * ```
 */
@Composable
fun Canvas(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Container({ canvasNode(XamlCanvas()) }, modifier, {}, content)
}

private fun canvasNode(canvas: XamlCanvas): ElementNode<XamlCanvas> =
    ElementNode(canvas, ChildSlot.Many(canvas.Children))
