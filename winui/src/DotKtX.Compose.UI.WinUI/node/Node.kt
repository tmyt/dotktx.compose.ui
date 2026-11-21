package dotktx.compose.ui.winui

import androidx.compose.runtime.ComposeNodeLifecycleCallback
import Microsoft.UI.Xaml.UIElement
import Microsoft.UI.Xaml.Controls.UIElementCollection

/**
 * A node of the tree that Compose maintains. The applier only sees [children]; WinUI elements are
 * attached through them.
 */
abstract class Node internal constructor(internal val children: ChildSlot)

/**
 * A node that owns one WinUI element. The wrapper exists because WinUI has several unrelated child
 * models (panels, content controls, leaves) and because each element needs a place for the applied
 * modifier, its event subscriptions and the latest callbacks.
 */
open class ElementNode<out E : UIElement> internal constructor(
    val element: E,
    children: ChildSlot,
) : Node(children), ComposeNodeLifecycleCallback {
    internal var appliedModifier: Modifier = Modifier
    private val releaseActions = ArrayList<AutoCloseable>()

    /** Registers a resource (typically an event subscription) closed when the node leaves the composition. */
    protected fun onRelease(action: AutoCloseable) {
        releaseActions.add(action)
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

    /** Leaf elements such as `TextBlock`. */
    class None(private val owner: String) : ChildSlot {
        override fun insert(index: Int, child: ElementNode<*>) {
            throw IllegalStateException("$owner cannot have child elements")
        }
        override fun remove(index: Int, count: Int) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun clear() {}
    }

    /** Exactly one child: `ContentControl.Content`, `Border.Child`, `Window.Content`, … */
    class Single(private val owner: String, private val set: (UIElement?) -> Unit) : ChildSlot {
        private var occupied = false

        override fun insert(index: Int, child: ElementNode<*>) {
            check(!occupied) { "$owner content must emit one element; wrap several in a StackPanel or Grid" }
            occupied = true
            set(child.element)
        }

        override fun remove(index: Int, count: Int) {
            if (count > 0) {
                occupied = false
                set(null)
            }
        }

        override fun move(from: Int, to: Int, count: Int) {
            throw IllegalStateException("$owner holds a single element; nothing can be moved")
        }

        override fun clear() {
            occupied = false
            set(null)
        }
    }

    /** `Panel.Children`. */
    class Many(private val collection: UIElementCollection) : ChildSlot {
        override fun insert(index: Int, child: ElementNode<*>) {
            collection.Insert(index, child.element)
        }

        override fun remove(index: Int, count: Int) {
            for (i in 0 until count) collection.RemoveAt(index)
        }

        /** Same contract as `AbstractApplier.move`: [to] is the destination index before the items are removed. */
        override fun move(from: Int, to: Int, count: Int) {
            val dest = if (from > to) to else to - count
            if (count == 1) {
                collection.Move(from.toUInt(), dest.toUInt())
            } else {
                val moved = ArrayList<UIElement>(count)
                for (i in 0 until count) moved.add(collection[from + i])
                for (i in 0 until count) collection.RemoveAt(from)
                for (i in 0 until count) collection.Insert(dest + i, moved[i])
            }
        }

        override fun clear() {
            collection.Clear()
        }
    }
}
