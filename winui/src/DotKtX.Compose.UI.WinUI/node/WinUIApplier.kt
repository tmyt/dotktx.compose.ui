package dotktx.compose.ui.winui

import androidx.compose.runtime.AbstractApplier

/**
 * Applies Compose tree changes to WinUI elements.
 *
 * Children are inserted bottom-up: a new subtree is assembled while detached and joins the live
 * visual tree once, so WinUI runs one Loaded/measure pass for it instead of invalidating the
 * parent's layout for every descendant.
 */
internal class WinUIApplier(root: Node) : AbstractApplier<Node>(root) {
    /** Single-child slots that held more than one child at some point of the current apply. */
    private val overfull = ArrayList<ChildSlot.Single>()

    override fun insertTopDown(index: Int, instance: Node) {}

    override fun insertBottomUp(index: Int, instance: Node) {
        val element = instance as? ElementNode<*>
            ?: throw IllegalStateException("Only element nodes can be children")
        val slot = current.children
        slot.insert(index, element)
        if (slot is ChildSlot.Single && slot.overfull) overfull.add(slot)
    }

    override fun onEndChanges() {
        if (overfull.isEmpty()) return
        val failed = overfull.firstOrNull { it.overfull }
        overfull.clear()
        if (failed != null) throw failed.overfullError()
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
