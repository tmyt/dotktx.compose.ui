package dotktx.compose.ui.appkit

import androidx.compose.runtime.AbstractApplier

/**
 * Applies Compose tree changes to AppKit views.
 *
 * Children are inserted bottom-up: a new subtree is assembled while detached and joins the live
 * view hierarchy once, so the enclosing panels lay out once for it instead of once per descendant.
 */
internal class AppKitApplier(root: Node) : AbstractApplier<Node>(root) {
    override fun insertTopDown(index: Int, instance: Node) {}

    override fun insertBottomUp(index: Int, instance: Node) {
        val element = instance as? ElementNode<*>
            ?: throw IllegalStateException("Only element nodes can be children")
        current.children.insert(index, element)
    }

    override fun remove(index: Int, count: Int) {
        current.children.remove(index, count)
    }

    override fun move(from: Int, to: Int, count: Int) {
        current.children.move(from, to, count)
    }

    override fun onClear() {
        root.children.clear()
    }
}

/** Applier of the application-level composition. Windows are effects, not nodes, so it never changes. */
internal class ApplicationApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) {}
    override fun insertBottomUp(index: Int, instance: Unit) {}
    override fun remove(index: Int, count: Int) {}
    override fun move(from: Int, to: Int, count: Int) {}
    override fun onClear() {}
}
