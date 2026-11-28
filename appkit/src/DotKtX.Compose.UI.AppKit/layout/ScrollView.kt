package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import dotktx.compose.ui.annotations.EmitsOneElement
import dotktx.compose.ui.annotations.AtMostOneElementContent
import AppKit.NSScrollView
import CoreGraphics.CGRect
import CoreGraphics.CGSize

/** Directions in which a [ScrollView] scrolls. */
enum class ScrollDirection { Vertical, Horizontal, Both }

/**
 * Scrolls one child (`NSScrollView`). [content] must emit at most one element; it starts at the top
 * left and is as large as it wants in the scrolled directions, and as wide or tall as the viewport in
 * the others. The scroll view reports its content's size as its own, so inside a `StackView` it grows
 * with the content: bound it with `Modifier.height(...)` or give it a `Stretch` slot.
 *
 * ```
 * ScrollView(Modifier.height(200.0)) { StackView { repeat(50) { Label("Row $it") } } }
 * ```
 */
@EmitsOneElement
@Composable
fun ScrollView(
    modifier: Modifier = Modifier,
    direction: ScrollDirection = ScrollDirection.Vertical,
    @AtMostOneElementContent content: @Composable () -> Unit,
) {
    Container({
        val scroll = ScrollPanel()
        ElementNode(scroll, ChildSlot.Single("ScrollView", scroll.documentHost))
    }, modifier, {
        set(direction) { (view as ScrollPanel).direction = it }
    }, content)
}

internal class ScrollPanel : NSScrollView() {
    val documentHost = ContentHost()

    var direction: ScrollDirection = ScrollDirection.Vertical
        set(value) {
            field = value
            HasVerticalScroller = value != ScrollDirection.Horizontal
            HasHorizontalScroller = value != ScrollDirection.Vertical
            NeedsLayout = true
            InvalidateIntrinsicContentSize()
            invalidateAncestors(this)
        }

    private var arranging = false

    init {
        DocumentView = documentHost
        HasVerticalScroller = true
        HasHorizontalScroller = false
        AutohidesScrollers = true
        DrawsBackground = false
    }

    private val scrollsHorizontally: Boolean get() = direction != ScrollDirection.Vertical
    private val scrollsVertically: Boolean get() = direction != ScrollDirection.Horizontal

    /** The document's size when the scrolled directions are unbounded and the others fit [viewport]. */
    private fun measureDocument(viewport: CGSize): CGSize {
        val inf = Double.POSITIVE_INFINITY
        return documentHost.measure(
            CGSize(
                if (scrollsHorizontally) inf else viewport.Width.Value,
                if (scrollsVertically) inf else viewport.Height.Value,
            ),
        )
    }

    private fun layoutDocument() {
        if (arranging) return
        arranging = true
        try {
            val viewport = ContentView.Bounds.Size
            val measured = measureDocument(viewport)
            documentHost.Frame = CGRect(
                0.0,
                0.0,
                maxOf(viewport.Width.Value, measured.Width.Value),
                maxOf(viewport.Height.Value, measured.Height.Value),
            )
            documentHost.NeedsLayout = true
        } finally {
            arranging = false
        }
    }

    override fun Layout() {
        super.Layout()
        layoutDocument()
    }

    override fun Tile() {
        super.Tile()
        layoutDocument()
    }

    /** The document's measured size, so the scroll view grows with its content unless the application bounds it. */
    override val IntrinsicContentSize: CGSize
        get() {
            val bounds = ContentView.Bounds.Size
            return measureDocument(
                CGSize(
                    if (bounds.Width.Value > 0.0) bounds.Width.Value else Double.POSITIVE_INFINITY,
                    if (bounds.Height.Value > 0.0) bounds.Height.Value else Double.POSITIVE_INFINITY,
                ),
            )
        }
}
