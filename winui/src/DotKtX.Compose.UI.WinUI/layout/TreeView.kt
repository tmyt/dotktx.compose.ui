package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import Microsoft.UI.Xaml.DependencyObject
import Microsoft.UI.Xaml.FrameworkElement
import Microsoft.UI.Xaml.Controls.ListViewBase
import Microsoft.UI.Xaml.Controls.TreeViewNode
import Microsoft.UI.Xaml.Controls.TreeViewSelectionMode
import Microsoft.UI.Xaml.Media.VisualTreeHelper
import System.Collections.Generic.IList
import Microsoft.UI.Xaml.Controls.TreeView as XamlTreeView

/** One item of a [TreeView] and the items nested under it. */
data class TreeItem<out T>(val value: T, val children: List<TreeItem<T>> = emptyList())

/**
 * A hierarchy of items that the user can expand and collapse (`TreeView`), with single selection
 * controlled by [selectedKey] (null for no selection). Selecting an item calls [onSelectedKeyChange]
 * with its key.
 *
 * Items are identified by [key], which must be unique across the whole tree; by default an item's
 * value is its key. Each realized item is composed by [itemContent] in a composition of its own, as
 * in [ListView]; an item's remembered state does not survive collapsing its parent or scrolling out
 * of view.
 *
 * Expansion is left to WinUI while [expandedKeys] is null. Otherwise it is controlled by
 * [expandedKeys], and expanding or collapsing an item by hand calls [onExpandedKeysChange] with the
 * new set.
 *
 * ```
 * TreeView(
 *     folders, selectedKey = current, onSelectedKeyChange = { current = it },
 *     key = { it.path }, expandedKeys = open, onExpandedKeysChange = { open = it },
 * ) { folder -> TextBlock(folder.name) }
 * ```
 */
@Composable
fun <T> TreeView(
    items: List<TreeItem<T>>,
    selectedKey: Any?,
    onSelectedKeyChange: (Any?) -> Unit,
    modifier: Modifier = Modifier,
    key: (T) -> Any = { it as Any },
    expandedKeys: Set<Any>? = null,
    onExpandedKeysChange: (Set<Any>) -> Unit = {},
    itemContent: @Composable (T) -> Unit,
) {
    val parent = rememberCompositionContext()
    val containers = remember { ContainerItems<T>(parent, "TreeView") }
    val scope = currentRecomposeScope
    Leaf({ TreeViewElementNode(XamlTreeView(), containers) }, modifier) {
        set(scope) { selection.scope = it; expansion.scope = it }
        set(onSelectedKeyChange) { this.onSelectedKeyChange = it }
        set(onExpandedKeysChange) { this.onExpandedKeysChange = it }
        reconcile {
            update(items, key, itemContent)
            selection.sync(selectedKey)
            expansionControlled = expandedKeys != null
            if (expandedKeys != null) expansion.sync(expandedKeys)
        }
    }
    DisposableEffect(containers) {
        onDispose { containers.dispose() }
    }
}

internal class TreeViewElementNode<T>(
    private val view: XamlTreeView,
    private val containers: ContainerItems<T>,
) : ElementNode<XamlTreeView>(view, ChildSlot.None("TreeView")) {
    var onSelectedKeyChange: (Any?) -> Unit = {}
    var onExpandedKeysChange: (Set<Any>) -> Unit = {}
    var expansionControlled = false
    private var nodes = HashMap<String, TreeViewNode>()
    /** The automation name of a node's item: the text of its composed content. */
    private val label: (String) -> String = { containers.compositions.textOf(it) }
    private var keys = HashMap<String, Any>()
    private val idsByKey = HashMap<Any, String>()

    val selection = ControlledValue<Any?>(
        read = { idOf(view.SelectedNode?.Content)?.let { keys[it] } },
        write = { key -> view.SelectedNode = key?.let { idsByKey[it] }?.let { nodes[it] } },
    )

    val expansion = ControlledValue<Set<Any>>(
        read = { expandedKeys() },
        write = { expanded -> for ((id, node) in nodes) node.IsExpanded = keys[id] in expanded },
    )

    init {
        view.SelectionMode = TreeViewSelectionMode.Single
        view.ItemTemplate = itemHostTemplate()
        onRelease(view.Loaded.subscribe { _, _ -> attachList() })
        onRelease(view.LayoutUpdated.subscribe { _, _ -> containers.sweep() })
        onRelease(view.SelectionChanged.subscribe { _, _ -> selection.userChanged(onSelectedKeyChange) })
        onRelease(view.Expanding.subscribe { _, _ -> if (expansionControlled) expansion.userChanged(onExpandedKeysChange) })
        onRelease(view.Collapsed.subscribe { _, _ -> if (expansionControlled) expansion.userChanged(onExpandedKeysChange) })
    }

    private var listAttached = false

    /**
     * `TreeView` is not an items control itself: it shows its nodes through a `ListViewBase` in its
     * template ("ListControl"), whose containers are prepared and recycled like those of [ListView].
     * The list exists once the template is applied, and its first containers are realized before the
     * tree is loaded, so those are bound by scanning them. `OnApplyTemplate` cannot be overridden
     * instead: a Kotlin subclass of `TreeView` is not known to XAML, which then refuses the
     * `TreeView` style for it.
     */
    private fun attachList() {
        if (listAttached) return
        val list = findByName(view, "ListControl") as? ListViewBase ?: return
        listAttached = true
        // The node's content is its id (an ItemKey); the host's composition shows the item instead.
        onRelease(list.ContainerContentChanging.subscribe { _, e -> containers.containerChanged(e, nodeId(e.Item)) })
        containers.scan(list.ItemsPanelRoot) { nodeId(list.ItemFromContainer(it)) }
    }

    private fun nodeId(item: Any?): String? = idOf((item as? TreeViewNode)?.Content)

    private fun expandedKeys(): Set<Any> {
        val result = HashSet<Any>()
        for ((id, node) in nodes) if (node.IsExpanded) keys[id]?.let { result.add(it) }
        return result
    }

    /**
     * Rebuilds the node tree from [items], reusing the node of every key that stays, so WinUI keeps
     * the containers, expansion and selection of unchanged parts. Restructuring can change WinUI's
     * selection and expansion; that is not a user change, and sync restores them.
     */
    fun update(items: List<TreeItem<T>>, key: (T) -> Any, content: @Composable (T) -> Unit) {
        val values = ArrayList<T>()
        fun collect(list: List<TreeItem<T>>) {
            for (item in list) {
                values.add(item.value)
                collect(item.children)
            }
        }
        collect(items)
        val compositions = containers.compositions
        selection.programmatic {
            expansion.programmatic {
                compositions.update(values, key, content)
                rebuild(items, values, key, compositions.ids)
            }
        }
        compositions.refresh()
    }

    /** [values] and [ids] list the items of [roots] depth first. */
    private fun rebuild(roots: List<TreeItem<T>>, values: List<T>, key: (T) -> Any, ids: List<String>) {
        val newNodes = ArrayList<TreeViewNode>(ids.size)
        val byId = HashMap<String, TreeViewNode>(ids.size * 2)
        val newKeys = HashMap<String, Any>(ids.size * 2)
        idsByKey.clear()
        for (i in ids.indices) {
            val id = ids[i]
            val k = key(values[i])
            newKeys[id] = k
            idsByKey[k] = id
            val node = nodes[id] ?: TreeViewNode().also { it.Content = ItemKey(id, label) }
            newNodes.add(node)
            byId[id] = node
        }
        // Detach every changed child list before filling any, so a node that moves to another parent
        // is never in two lists at once.
        val changed = ArrayList<Pair<IList<TreeViewNode>, List<TreeViewNode>>>()
        var next = 0
        fun plan(target: IList<TreeViewNode>, children: List<TreeItem<T>>) {
            val wanted = ArrayList<TreeViewNode>(children.size)
            for (child in children) {
                val node = newNodes[next++]
                wanted.add(node)
                plan(node.Children, child.children)
            }
            var same = target.Count == wanted.size
            if (same) for (i in wanted.indices) if (target[i] !== wanted[i]) { same = false; break }
            if (!same) changed.add(target to wanted)
        }
        plan(view.RootNodes, roots)
        for ((id, node) in nodes) if (id !in byId) node.Children.Clear()
        for ((target, _) in changed) target.Clear()
        for ((target, wanted) in changed) for (node in wanted) target.Add(node)
        nodes = byId
        keys = newKeys
    }
}

/** The first element named [name] in the visual tree under [root], depth first. */
internal fun findByName(root: DependencyObject, name: String): DependencyObject? {
    val count = VisualTreeHelper.GetChildrenCount(root)
    for (i in 0 until count) {
        val child = VisualTreeHelper.GetChild(root, i)
        if ((child as? FrameworkElement)?.Name == name) return child
        val found = findByName(child, name)
        if (found != null) return found
    }
    return null
}
