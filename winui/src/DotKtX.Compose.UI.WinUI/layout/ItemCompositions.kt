package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.key
import System.Collections.ObjectModel.ObservableCollection
import System.Collections.Specialized.NotifyCollectionChangedAction
import System.Collections.Specialized.NotifyCollectionChangedEventArgs
import Microsoft.UI.Xaml.DependencyObject
import Microsoft.UI.Xaml.Controls.Grid
import Microsoft.UI.Xaml.Controls.TextBlock as XamlTextBlock
import Microsoft.UI.Xaml.Media.VisualTreeHelper
import Microsoft.UI.Xaml.Controls.IKeyIndexMapping

/**
 * The items source of a list whose elements WinUI realizes: one string id per item.
 *
 * [replace] announces every change as a reset; because the source maps ids to indices
 * (`IKeyIndexMapping`), `ItemsRepeater` keeps the elements of ids that are still present instead of
 * recreating them. `ListViewBase` ignores `IKeyIndexMapping` and recreates every container on a
 * reset, so lists built on it use [edit], which announces insertions and removals one by one.
 */
internal class KeyedSource(private val label: ((String) -> String)? = null) : ObservableCollection<Any?>(), IKeyIndexMapping {
    private var indexById = HashMap<String, Int>()

    /** With [label], items are [ItemKey]s; otherwise they are the ids themselves. */
    private fun itemFor(id: String): Any = if (label != null) ItemKey(id, label) else id

    /** The id of the item at [index]. */
    fun idAt(index: Int): String = idOf(Items[index]) ?: ""

    fun replace(ids: List<String>) {
        Items.Clear()
        for (id in ids) Items.Add(itemFor(id))
        reindex(ids)
        OnCollectionChanged(NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Reset))
    }

    /**
     * Changes the contents to [ids] with removals and insertions, so that the containers of ids that
     * stay keep their items. An id that moves is removed and inserted again. When most of the list
     * changes, a single reset is cheaper than many notifications.
     */
    fun edit(ids: List<String>) {
        val current = ArrayList<String>(Count)
        for (i in 0 until Count) current.add(idAt(i))
        val target = HashSet<String>(ids.size * 2)
        target.addAll(ids)
        val kept = current.filter { it in target }
        val removals = current.size - kept.size
        // Ids that keep their relative order stay; the others are reinserted at their new position.
        val stay = longestCommonOrder(kept, ids)
        val reinserted = kept.size - stay.size
        val insertions = ids.size - stay.size
        if (removals + reinserted + insertions > maxOf(32, ids.size / 2)) {
            replace(ids)
            return
        }
        for (i in current.size - 1 downTo 0) {
            if (current[i] !in stay) RemoveAt(i)
        }
        for (i in ids.indices) {
            if (i >= Count || idAt(i) != ids[i]) Insert(i, itemFor(ids[i]))
        }
        reindex(ids)
    }

    private fun reindex(ids: List<String>) {
        val index = HashMap<String, Int>(ids.size * 2)
        for (i in ids.indices) index[ids[i]] = i
        indexById = index
    }

    fun indexOf(id: String): Int = indexById[id] ?: -1

    override fun KeyFromIndex(index: Int): String = idAt(index)

    override fun IndexFromKey(key: String): Int = indexOf(key)
}

/** The id of an item of a [KeyedSource]. */
internal fun idOf(item: Any?): String? = (item as? ItemKey)?.id ?: item as? String

/**
 * An item of a [KeyedSource] for lists whose containers show an item template. Its text is what
 * WinUI uses as the container's automation name; a string id would name every item after the
 * internal id. [label] gives the text of the item's composed content.
 */
internal class ItemKey(val id: String, private val label: (String) -> String) {
    override fun toString(): String = label(id)
}

/**
 * The ids of [kept] that appear in the same relative order in [ids]: a longest increasing
 * subsequence of their new positions. Both lists hold the same set of distinct ids, except that
 * [ids] may hold more.
 */
private fun longestCommonOrder(kept: List<String>, ids: List<String>): Set<String> {
    val position = HashMap<String, Int>(ids.size * 2)
    for (i in ids.indices) position[ids[i]] = i
    val n = kept.size
    val tails = IntArray(n)
    val previous = IntArray(n)
    var length = 0
    for (i in 0 until n) {
        val p = position[kept[i]]!!
        var lo = 0
        var hi = length
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (position[kept[tails[mid]]]!! < p) lo = mid + 1 else hi = mid
        }
        previous[i] = if (lo > 0) tails[lo - 1] else -1
        tails[lo] = i
        if (lo == length) length++
    }
    val result = HashSet<String>(length * 2)
    var i = if (length > 0) tails[length - 1] else -1
    while (i >= 0) {
        result.add(kept[i])
        i = previous[i]
    }
    return result
}

/**
 * The items of a list whose elements WinUI realizes, and the compositions of the realized elements.
 *
 * Each realized element is an [ItemHost] whose composition shows the item with the id the host is
 * bound to. The list gives the items on every recomposition ([update]) and binds and unbinds hosts as
 * WinUI realizes and recycles elements.
 */
internal class ItemCompositions<T>(private val owner: String) {
    var ids: List<String> = emptyList()
        private set
    private var items: List<T> = emptyList()
    private var content: @Composable (T) -> Unit = {}
    private val idsByKey = HashMap<Any, String>()
    private var nextId = 0
    private var indexById = HashMap<String, Int>()
    private val realized = HashMap<ItemHost, String>()

    /**
     * Takes the items of a recomposition of the list. Returns true when the ids changed; the list then
     * updates its items source, and calls [refresh] afterwards.
     */
    fun update(newItems: List<T>, key: ((T) -> Any)?, newContent: @Composable (T) -> Unit): Boolean {
        items = newItems
        content = newContent
        val newIds = if (key == null) List(newItems.size) { "#$it" } else idsFor(newItems, key)
        if (newIds == ids) return false
        ids = newIds
        val index = HashMap<String, Int>(newIds.size * 2)
        for (i in newIds.indices) index[newIds[i]] = i
        indexById = index
        return true
    }

    /** Elements that stay realized show their item's current data in place. */
    fun refresh() {
        for ((host, id) in realized) {
            val index = indexOf(id)
            if (index >= 0) show(host, id, index)
        }
    }

    fun indexOf(id: String): Int = indexById[id] ?: -1

    /** Stable string ids for user keys; ids of keys that left the list are dropped. */
    private fun idsFor(newItems: List<T>, key: (T) -> Any): List<String> {
        val present = HashSet<Any>(newItems.size * 2)
        val result = ArrayList<String>(newItems.size)
        for (item in newItems) {
            val k = key(item)
            require(present.add(k)) { "$owner keys must be unique; duplicate key: $k" }
            result.add(idsByKey.getOrPut(k) { "k" + nextId++ })
        }
        idsByKey.keys.retainAll(present)
        return result
    }

    /** [host] now shows the item with [id]. */
    fun bind(host: ItemHost, id: String) {
        realized[host] = id
        val index = indexOf(id)
        if (index >= 0) show(host, id, index)
    }

    /** The id [host] is bound to, or null while it is recycled. */
    fun boundId(host: ItemHost): String? = realized[host]

    /** [host] was recycled; it keeps its composition until it is bound again. */
    fun unbind(host: ItemHost) {
        realized.remove(host)
    }

    /** The text shown by the composed content of the item with [id], or "" while it is not realized. */
    fun textOf(id: String): String {
        for ((host, bound) in realized) if (bound == id) return plainText(host.element)
        return ""
    }

    private fun show(host: ItemHost, id: String, index: Int) {
        val item = items[index]
        val itemContent = content
        // Keyed by id: a recycled host must not hand the state of its previous item to the next one.
        host.composition.setContent { key(id) { itemContent(item) } }
    }

    fun clear() {
        realized.clear()
    }
}

/**
 * The element of one realized item and the composition that fills it.
 *
 * The host is a `Grid` (several children allowed) rather than a single-child `Border`: when a
 * recycled host switches to another item, the new item's element is inserted before the old one is
 * removed.
 */
internal class ItemHost(parent: CompositionContext, val element: Grid = Grid()) {
    val composition = Composition(WinUIApplier(ItemRoot(element)), parent)
}

private class ItemRoot(grid: Grid) : Node(ChildSlot.Many(grid.Children))

/** The texts of the `TextBlock`s under [root], in tree order, separated by spaces. */
internal fun plainText(root: DependencyObject): String {
    val parts = ArrayList<String>()
    fun collect(element: DependencyObject) {
        val text = (element as? XamlTextBlock)?.Text
        if (!text.isNullOrEmpty()) parts.add(text)
        for (i in 0 until VisualTreeHelper.GetChildrenCount(element)) collect(VisualTreeHelper.GetChild(element, i))
    }
    collect(root)
    return parts.joinToString(" ")
}
