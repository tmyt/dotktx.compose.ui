package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import CoreGraphics.CGRect
import CoreGraphics.CGSize

/** Direction in which a [StackView] lays out its children. */
enum class Orientation { Vertical, Horizontal }

/**
 * Lays out children one after another along [orientation], [spacing] points apart (hidden children
 * take no space). Each child gets its measured size along the main axis; across it the child is
 * placed by its own `Modifier.align`, or by [alignment] (`alignment.horizontal` in a vertical stack,
 * `alignment.vertical` in a horizontal one; `Stretch` fills the stack).
 *
 * ```
 * StackView(spacing = 8.0) { Label("Name"); TextField(name) { name = it } }
 * ```
 */
@Composable
fun StackView(
    modifier: Modifier = Modifier,
    orientation: Orientation = Orientation.Vertical,
    spacing: Double = 0.0,
    alignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    Container({
        val panel = StackPanel()
        ElementNode(panel, ChildSlot.Many(panel))
    }, modifier, {
        set(orientation) { (view as StackPanel).orientation = it }
        set(spacing) { (view as StackPanel).spacing = it }
        set(alignment) { (view as StackPanel).alignment = it }
    }, content)
}

internal class StackPanel : Panel() {
    var orientation: Orientation = Orientation.Vertical
        set(value) { field = value; invalidateLayout() }
    var spacing: Double = 0.0
        set(value) { field = value; invalidateLayout() }
    var alignment: Alignment = Alignment.TopStart
        set(value) { field = value; invalidateLayout() }

    private val vertical: Boolean get() = orientation == Orientation.Vertical

    override fun measure(available: CGSize): CGSize {
        val pad = padding
        val crossAvailable = if (vertical) shrink(available.Width.Value, pad.horizontal) else shrink(available.Height.Value, pad.vertical)
        var main = 0.0
        var cross = 0.0
        var count = 0
        for (child in children) {
            if (child.view.Hidden) continue
            val m = child.layout.margin
            val s = measureChild(child, childAvailable(crossAvailable, m))
            main += if (vertical) s.Height.Value + m.vertical else s.Width.Value + m.horizontal
            cross = maxOf(cross, if (vertical) s.Width.Value + m.horizontal else s.Height.Value + m.vertical)
            count++
        }
        if (count > 1) main += spacing * (count - 1)
        return if (vertical) CGSize(cross + pad.horizontal, main + pad.vertical) else CGSize(main + pad.horizontal, cross + pad.vertical)
    }

    override fun arrange(bounds: CGRect) {
        val inner = deflate(bounds, padding)
        val crossSize = if (vertical) inner.Width.Value else inner.Height.Value
        var offset = if (vertical) inner.Y.Value else inner.X.Value
        for (child in children) {
            if (child.view.Hidden) continue
            val m = child.layout.margin
            val s = measureChild(child, childAvailable(crossSize, m))
            if (vertical) {
                val slotMain = s.Height.Value + m.vertical
                arrangeChild(child, CGRect(inner.X.Value, offset, crossSize, slotMain), alignment)
                offset += slotMain + spacing
            } else {
                val slotMain = s.Width.Value + m.horizontal
                arrangeChild(child, CGRect(offset, inner.Y.Value, slotMain, crossSize), alignment)
                offset += slotMain + spacing
            }
        }
    }

    /** Space for a child itself: unbounded along the main axis, the cross space minus its margins across it. */
    private fun childAvailable(cross: Double, m: Insets): CGSize {
        val inf = Double.POSITIVE_INFINITY
        return if (vertical) CGSize(shrink(cross, m.horizontal), inf) else CGSize(inf, shrink(cross, m.vertical))
    }
}
