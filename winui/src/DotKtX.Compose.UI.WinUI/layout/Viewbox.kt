package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import Microsoft.UI.Xaml.Controls.StretchDirection
import Microsoft.UI.Xaml.Media.Stretch
import Microsoft.UI.Xaml.Controls.Viewbox as XamlViewbox

/**
 * Scales one child to fill the space it is given (`Viewbox`). [stretch] decides how the child's
 * aspect ratio is kept; [stretchDirection] limits scaling to growing or shrinking. [content] must
 * emit at most one element.
 *
 * ```
 * Viewbox(Modifier.size(200.0, 100.0)) { TextBlock("Big") }
 * ```
 */
@Composable
fun Viewbox(
    modifier: Modifier = Modifier,
    stretch: Stretch = Stretch.Uniform,
    stretchDirection: StretchDirection = StretchDirection.Both,
    content: @Composable () -> Unit,
) {
    Container({ viewboxNode(XamlViewbox()) }, modifier, {
        set(stretch) { element.Stretch = it }
        set(stretchDirection) { element.StretchDirection = it }
    }, content)
}

private fun viewboxNode(viewbox: XamlViewbox): ElementNode<XamlViewbox> =
    ElementNode(viewbox, ChildSlot.Single("Viewbox") { viewbox.Child = it })
