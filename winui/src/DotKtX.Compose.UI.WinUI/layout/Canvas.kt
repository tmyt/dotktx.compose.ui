package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.FrameworkElement
import Microsoft.UI.Xaml.Controls.Canvas as XamlCanvas

/**
 * Places its children at fixed coordinates (`Canvas`). Each child sets its position with
 * [CanvasScope.position]; children without one sit at the top-left corner. The canvas does not
 * size itself to its children, so give it a size when its parent does not stretch it.
 *
 * ```
 * Canvas(Modifier.size(300.0, 200.0)) {
 *     TextBlock("Here", Modifier.position(left = 40.0, top = 20.0))
 * }
 * ```
 */
@EmitsOneElement
@Composable
fun Canvas(
    modifier: Modifier = Modifier,
    content: @Composable CanvasScope.() -> Unit,
) {
    Container({ canvasNode(XamlCanvas()) }, modifier, {}) { CanvasScope.content() }
}

private fun canvasNode(canvas: XamlCanvas): ElementNode<XamlCanvas> =
    ElementNode(canvas, ChildSlot.Many(canvas.Children))

/** Receiver of [Canvas]'s content. */
@LayoutScopeMarker
object CanvasScope {
    /**
     * Position of this child, in effective pixels from the canvas's top-left corner (`Canvas.Left`,
     * `Canvas.Top`). [zIndex] orders overlapping children (`Canvas.ZIndex`, higher on top); `null`
     * keeps the order of the children.
     */
    fun Modifier.position(left: Double, top: Double, zIndex: Int? = null): Modifier =
        this then CanvasPositionModifier(left, top, zIndex)
}

private data class CanvasPositionModifier(val left: Double, val top: Double, val zIndex: Int?) : ElementModifier {
    override val key: Any get() = CanvasPositionModifier::class

    override fun apply(element: FrameworkElement) {
        XamlCanvas.SetLeft(element, left)
        XamlCanvas.SetTop(element, top)
        if (zIndex == null) element.ClearValue(XamlCanvas.ZIndexProperty) else XamlCanvas.SetZIndex(element, zIndex)
    }

    override fun reset(element: FrameworkElement) {
        element.ClearValue(XamlCanvas.LeftProperty)
        element.ClearValue(XamlCanvas.TopProperty)
        element.ClearValue(XamlCanvas.ZIndexProperty)
    }
}
