package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import dotktx.compose.ui.annotations.AtMostOneElementContent
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.Controls.ScrollBarVisibility
import Microsoft.UI.Xaml.Controls.ScrollMode
import Microsoft.UI.Xaml.Controls.ScrollViewer as XamlScrollViewer

/** Directions in which a [ScrollViewer] scrolls. */
enum class ScrollDirection { Vertical, Horizontal, Both }

/**
 * Scrolls one child that is larger than the viewport (`ScrollViewer`). [content] must emit at most one
 * element; in a scrolling direction it is measured with unlimited space.
 */
@EmitsOneElement
@Composable
fun ScrollViewer(
    modifier: Modifier = Modifier,
    direction: ScrollDirection = ScrollDirection.Vertical,
    @AtMostOneElementContent content: @Composable () -> Unit,
) {
    Container({ scrollNode(XamlScrollViewer()) }, modifier, {
        set(direction) {
            val vertical = it != ScrollDirection.Horizontal
            val horizontal = it != ScrollDirection.Vertical
            element.VerticalScrollMode = if (vertical) ScrollMode.Enabled else ScrollMode.Disabled
            element.VerticalScrollBarVisibility = if (vertical) ScrollBarVisibility.Auto else ScrollBarVisibility.Disabled
            element.HorizontalScrollMode = if (horizontal) ScrollMode.Enabled else ScrollMode.Disabled
            element.HorizontalScrollBarVisibility = if (horizontal) ScrollBarVisibility.Auto else ScrollBarVisibility.Disabled
        }
    }, content)
}

private fun scrollNode(viewer: XamlScrollViewer): ElementNode<XamlScrollViewer> =
    ElementNode(viewer, ChildSlot.Single("ScrollViewer") { viewer.Content = it })
