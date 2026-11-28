package dotktx.compose.ui.appkit

import androidx.compose.runtime.ComposeNodeLifecycleCallback
import AppKit.NSView

/**
 * A node of the tree that Compose maintains. The applier only sees [children]; AppKit views are
 * attached through them.
 */
abstract class Node internal constructor(internal val children: ChildSlot)

/**
 * A node that owns one AppKit view. The wrapper exists because containers hold children in
 * different ways (a [Panel]'s children, a single content slot, none) and because each view needs a
 * place for the applied modifier, its layout properties, its event subscriptions and the latest
 * callbacks.
 */
open class ElementNode<out V : NSView> internal constructor(
    val view: V,
    children: ChildSlot,
) : Node(children), ComposeNodeLifecycleCallback {
    internal var appliedModifier: Modifier = Modifier

    /** Layout properties read by the enclosing [Panel]: alignment, margin, size bounds, padding. */
    internal var layout: LayoutParams = LayoutParams()

    private val releaseActions = ArrayList<AutoCloseable>()

    /** Registers a resource (typically an event subscription) closed when the node leaves the composition. */
    protected fun onRelease(action: AutoCloseable) {
        releaseActions.add(action)
    }

    /** Asks the enclosing panels to measure and arrange again, after a property of this view changed. */
    internal fun invalidateLayout() {
        (view as? Panel)?.invalidateLayout()
        var parent = view.Superview
        while (parent != null) {
            if (parent is Panel) {
                parent.invalidateLayout()
                return
            }
            parent = parent.Superview
        }
    }

    override fun onRelease() {
        for (action in releaseActions) action.close()
        releaseActions.clear()
    }

    override fun onDeactivate() {}

    override fun onReuse() {}
}

/** How a node holds its children. */
internal sealed interface ChildSlot {
    fun insert(index: Int, child: ElementNode<*>)
    fun remove(index: Int, count: Int)
    fun move(from: Int, to: Int, count: Int)
    fun clear()

    /** Leaf views such as `Label`. */
    class None(private val owner: String) : ChildSlot {
        override fun insert(index: Int, child: ElementNode<*>) {
            throw IllegalStateException("$owner cannot have child elements")
        }
        override fun remove(index: Int, count: Int) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun clear() {}
    }

    /**
     * At most one child, which fills a [ContentHost]: window content, `ScrollView` content, `Box`
     * content, …
     *
     * Within one apply the slot can briefly hold two children: when content switches between two
     * separate `if`s, the insert of the new element can come before the remove of the old one. The
     * child is handed to the host whenever one is left, and [AppKitApplier] reports a slot that still
     * holds more than one once the changes are applied.
     */
    class Single(private val owner: String, private val host: ContentHost) : ChildSlot {
        private val nodes = ArrayList<ElementNode<*>>(1)
        private var shown: ElementNode<*>? = null

        val overfull: Boolean get() = nodes.size > 1

        override fun insert(index: Int, child: ElementNode<*>) {
            nodes.add(index, child)
            show()
        }

        override fun remove(index: Int, count: Int) {
            for (i in 0 until count) nodes.removeAt(index)
            show()
        }

        /** Same contract as `AbstractApplier.move`: [to] is the destination index before the items are removed. */
        override fun move(from: Int, to: Int, count: Int) {
            val dest = if (from > to) to else to - count
            val moved = ArrayList<ElementNode<*>>(count)
            for (i in 0 until count) moved.add(nodes.removeAt(from))
            nodes.addAll(dest, moved)
        }

        override fun clear() {
            nodes.clear()
            show()
        }

        /** Thrown by [AppKitApplier] when the applied changes leave more than one child. */
        fun overfullError(): IllegalStateException {
            val types = nodes.joinToString { it.view::class.simpleName ?: "?" }
            return IllegalStateException(
                "$owner content emitted ${nodes.size} elements ($types) but holds one; wrap them in a StackView",
            )
        }

        private fun show() {
            if (nodes.size > 1) return
            val child = nodes.firstOrNull()
            if (child !== shown) {
                shown = child
                host.clear()
                if (child != null) host.insert(0, child)
            }
        }
    }

    /** Children of a [Panel]. */
    class Many(private val panel: Panel) : ChildSlot {
        override fun insert(index: Int, child: ElementNode<*>) = panel.insert(index, child)
        override fun remove(index: Int, count: Int) = panel.remove(index, count)
        override fun move(from: Int, to: Int, count: Int) = panel.move(from, to, count)
        override fun clear() = panel.clear()
    }
}
