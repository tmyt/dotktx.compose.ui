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

    /** Exactly one child, which fills a [ContentHost]: window content, `ScrollView` content, `Box` content, … */
    class Single(private val owner: String, private val host: ContentHost) : ChildSlot {
        private var occupied = false

        override fun insert(index: Int, child: ElementNode<*>) {
            check(!occupied) { "$owner content must emit one element; wrap several in a StackView" }
            occupied = true
            host.insert(0, child)
        }

        override fun remove(index: Int, count: Int) {
            if (count > 0) {
                occupied = false
                host.clear()
            }
        }

        override fun move(from: Int, to: Int, count: Int) {
            throw IllegalStateException("$owner holds a single element; nothing can be moved")
        }

        override fun clear() {
            occupied = false
            host.clear()
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
