package dotktx.compose.ui.appkit

import AppKit.NSView
import CoreGraphics.CGRect
import CoreGraphics.CGSize

/**
 * Base of every container that holds Compose children. A panel measures and arranges its children
 * itself, from each child's [LayoutParams] (alignment, margin, size bounds), the way a WinUI panel
 * does. Panels are flipped: the origin is the top-left corner and y grows downwards.
 *
 * Native leaf views are measured with [measureChild]; panels nest by calling each other's [measure].
 */
abstract class Panel internal constructor() : NSView() {
    /** Children in composition order. */
    internal val children = ArrayList<ElementNode<*>>()

    /** Space between the panel's edge and its children (`Modifier.padding`). */
    internal var padding: Insets = Insets.Zero
        set(value) {
            field = value
            invalidateLayout()
        }

    internal fun insert(index: Int, child: ElementNode<*>) {
        TODO("U2")
    }

    internal fun remove(index: Int, count: Int) {
        TODO("U2")
    }

    /** Same contract as `AbstractApplier.move`: [to] is the destination index before the items are removed. */
    internal fun move(from: Int, to: Int, count: Int) {
        TODO("U2")
    }

    internal fun clear() {
        TODO("U2")
    }

    /**
     * The size this panel wants inside [available] (an infinite or `NaN` component is unbounded):
     * the children's sizes with their margins, plus [padding].
     */
    internal abstract fun measure(available: CGSize): CGSize

    /** Places the children inside [bounds] (the panel's full bounds; [padding] is subtracted here). */
    internal abstract fun arrange(bounds: CGRect)

    /** Marks this panel and the panels above it for a new measure and arrange pass. */
    internal fun invalidateLayout() {
        TODO("U2")
    }

    override fun Layout() {
        super.Layout()
        arrange(Bounds)
    }

    override val IsFlipped: Boolean get() = true

    override val IntrinsicContentSize: CGSize get() = measure(CGSize(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY))
}

/**
 * Measures one child for [available] (the space left for the child itself, margins already removed),
 * honoring the fixed and bounded sizes of its [LayoutParams].
 */
internal fun measureChild(child: ElementNode<*>, available: CGSize): CGSize {
    TODO("U2")
}

/**
 * Places [child] inside [slot]: subtracts its margin, sizes it per its measured size, fixed size and
 * alignment ([defaultAlignment] when it has none), then sets `view.Frame`.
 */
internal fun arrangeChild(child: ElementNode<*>, slot: CGRect, defaultAlignment: Alignment) {
    TODO("U2")
}

/**
 * A panel with at most one child that fills it by default. The root of a window, the content of
 * `ScrollView` and `Box`, and `ComposeHost` are content hosts.
 */
class ContentHost internal constructor() : Panel() {
    override fun measure(available: CGSize): CGSize {
        TODO("U2")
    }

    override fun arrange(bounds: CGRect) {
        TODO("U2")
    }
}
