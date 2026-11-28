package dotktx.compose.ui.appkit

import AppKit.NSClipView
import AppKit.NSControl
import AppKit.NSView
import AppKit.NSWindowOrderingMode
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
        val view = child.view
        children.add(index, child)
        if (index == 0) {
            if (children.size > 1) AddSubview(view, NSWindowOrderingMode.Below, children[1].view) else AddSubview(view)
        } else {
            AddSubview(view, NSWindowOrderingMode.Above, children[index - 1].view)
        }
        invalidateLayout()
    }

    internal fun remove(index: Int, count: Int) {
        repeat(count) {
            val child = children.removeAt(index)
            child.view.RemoveFromSuperview()
        }
        invalidateLayout()
    }

    /** Same contract as `AbstractApplier.move`: [to] is the destination index before the items are removed. */
    internal fun move(from: Int, to: Int, count: Int) {
        if (count <= 0 || from == to) return
        val moved = ArrayList<ElementNode<*>>(children.subList(from, from + count))
        repeat(count) { children.removeAt(from) }
        val target = if (from > to) to else to - count
        children.addAll(target, moved)
        // Re-adding every child in order restores the z-order of composition order.
        for (child in children) {
            child.view.RemoveFromSuperview()
            AddSubview(child.view)
        }
        invalidateLayout()
    }

    internal fun clear() {
        for (child in children) child.view.RemoveFromSuperview()
        children.clear()
        invalidateLayout()
    }

    /**
     * The size this panel wants inside [available] (an infinite or `NaN` component is unbounded):
     * the children's sizes with their margins, plus [padding].
     */
    internal abstract fun measure(available: CGSize): CGSize

    /** Places the children inside [bounds] (the panel's full bounds; [padding] is subtracted here). */
    internal abstract fun arrange(bounds: CGRect)

    private var invalidating = false

    /** Marks this panel and the panels above it for a new measure and arrange pass. */
    internal fun invalidateLayout() {
        if (invalidating) return
        invalidating = true
        try {
            NeedsLayout = true
            InvalidateIntrinsicContentSize()
            invalidateAncestors(this)
        } finally {
            invalidating = false
        }
    }

    override fun Layout() {
        super.Layout()
        arrange(Bounds)
    }

    override val IsFlipped: Boolean get() = true

    override val IntrinsicContentSize: CGSize get() = measure(CGSize(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY))
}

/**
 * Marks the ancestors of [from] for layout. Panels, scroll views and the clip view between a scroll
 * view and its document are walked through; the walk ends after the first other ancestor (which
 * still gets `NeedsLayout`, e.g. the view that hosts the composition).
 */
internal fun invalidateAncestors(from: NSView) {
    var parent = from.Superview
    while (parent != null) {
        parent.NeedsLayout = true
        if (parent is Panel || parent is ScrollPanel) {
            parent.InvalidateIntrinsicContentSize()
        } else if (parent !is NSClipView) {
            return
        }
        parent = parent.Superview
    }
}

private fun unbounded(v: Double): Boolean = v.isNaN() || v.isInfinite()

private fun clamp(v: Double, min: Double, max: Double): Double {
    var r = v
    if (!min.isNaN()) r = maxOf(r, min)
    if (!max.isNaN()) r = minOf(r, max)
    return r
}

/** [v] minus [by], staying unbounded when it is and never negative. */
internal fun shrink(v: Double, by: Double): Double = if (unbounded(v)) v else maxOf(0.0, v - by)

internal fun deflate(r: CGRect, pad: Insets): CGRect = CGRect(
    r.X.Value + pad.start,
    r.Y.Value + pad.top,
    maxOf(0.0, r.Width.Value - pad.horizontal),
    maxOf(0.0, r.Height.Value - pad.vertical),
)

/**
 * Measures one child for [available] (the space left for the child itself, margins already removed),
 * honoring the fixed and bounded sizes of its [LayoutParams]. A hidden child measures as zero.
 */
internal fun measureChild(child: ElementNode<*>, available: CGSize): CGSize {
    val view = child.view
    if (view.Hidden) return CGSize(0.0, 0.0)
    val p = child.layout
    var aw = available.Width.Value
    var ah = available.Height.Value
    if (!p.maxWidth.isNaN()) aw = if (unbounded(aw)) p.maxWidth else minOf(aw, p.maxWidth)
    if (!p.maxHeight.isNaN()) ah = if (unbounded(ah)) p.maxHeight else minOf(ah, p.maxHeight)
    if (!p.width.isNaN()) aw = p.width
    if (!p.height.isNaN()) ah = p.height

    var w: Double
    var h: Double
    if (view is Panel) {
        val s = view.measure(CGSize(aw, ah))
        w = s.Width.Value
        h = s.Height.Value
    } else if (view is NSControl) {
        val s = view.SizeThatFits(CGSize(if (unbounded(aw)) 1e9 else aw, if (unbounded(ah)) 1e9 else ah))
        w = s.Width.Value
        h = s.Height.Value
    } else {
        val intrinsic = view.IntrinsicContentSize
        val fitting = view.FittingSize
        w = if (intrinsic.Width.Value >= 0.0) intrinsic.Width.Value else fitting.Width.Value
        h = if (intrinsic.Height.Value >= 0.0) intrinsic.Height.Value else fitting.Height.Value
    }
    if (!p.width.isNaN()) w = p.width
    if (!p.height.isNaN()) h = p.height
    w = clamp(w, p.minWidth, p.maxWidth)
    h = clamp(h, p.minHeight, p.maxHeight)
    return CGSize(maxOf(0.0, w), maxOf(0.0, h))
}

/**
 * Places [child] inside [slot]: subtracts its margin, sizes it per its measured size, fixed size and
 * alignment ([defaultAlignment] when it has none), then sets `view.Frame`.
 */
internal fun arrangeChild(child: ElementNode<*>, slot: CGRect, defaultAlignment: Alignment) {
    val view = child.view
    if (view.Hidden) return
    val p = child.layout
    val m = p.margin
    val sx = slot.X.Value + m.start
    val sy = slot.Y.Value + m.top
    val sw = maxOf(0.0, slot.Width.Value - m.horizontal)
    val sh = maxOf(0.0, slot.Height.Value - m.vertical)
    val alignment = p.alignment ?: defaultAlignment
    val measured = measureChild(child, CGSize(sw, sh))

    var w: Double
    var x = sx
    if (alignment.horizontal == Alignment.Horizontal.Stretch) {
        w = clamp(sw, p.minWidth, p.maxWidth)
        if (!p.width.isNaN()) w = p.width
    } else {
        w = measured.Width.Value
    }
    w = maxOf(0.0, w)
    when (alignment.horizontal) {
        Alignment.Horizontal.Center -> x = sx + (sw - w) / 2
        Alignment.Horizontal.Trailing -> x = sx + sw - w
        else -> {}
    }

    var h: Double
    var y = sy
    if (alignment.vertical == Alignment.Vertical.Stretch) {
        h = clamp(sh, p.minHeight, p.maxHeight)
        if (!p.height.isNaN()) h = p.height
    } else {
        h = measured.Height.Value
    }
    h = maxOf(0.0, h)
    when (alignment.vertical) {
        Alignment.Vertical.Center -> y = sy + (sh - h) / 2
        Alignment.Vertical.Bottom -> y = sy + sh - h
        else -> {}
    }
    view.Frame = CGRect(x, y, w, h)
}

/**
 * A panel with at most one child that fills it by default. The root of a window, the content of
 * `ScrollView` and `Box`, and `ComposeHost` are content hosts.
 */
class ContentHost internal constructor() : Panel() {
    override fun measure(available: CGSize): CGSize {
        val pad = padding
        val child = children.firstOrNull()
        if (child == null || child.view.Hidden) return CGSize(pad.horizontal, pad.vertical)
        val m = child.layout.margin
        val s = measureChild(
            child,
            CGSize(shrink(available.Width.Value, pad.horizontal + m.horizontal), shrink(available.Height.Value, pad.vertical + m.vertical)),
        )
        return CGSize(s.Width.Value + m.horizontal + pad.horizontal, s.Height.Value + m.vertical + pad.vertical)
    }

    override fun arrange(bounds: CGRect) {
        val child = children.firstOrNull() ?: return
        arrangeChild(child, deflate(bounds, padding), Alignment.Stretch)
    }
}
