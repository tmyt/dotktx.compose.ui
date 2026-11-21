package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import System.Collections.ObjectModel.ObservableCollection
import System.Collections.Specialized.NotifyCollectionChangedAction
import System.Collections.Specialized.NotifyCollectionChangedEventArgs
import Microsoft.UI.Xaml.ElementFactoryGetArgs
import Microsoft.UI.Xaml.ElementFactoryRecycleArgs
import Microsoft.UI.Xaml.IElementFactory
import Microsoft.UI.Xaml.UIElement
import Microsoft.UI.Xaml.Controls.Grid
import Microsoft.UI.Xaml.Controls.IKeyIndexMapping
import Microsoft.UI.Xaml.Controls.ItemsRepeater
import Microsoft.UI.Xaml.Controls.Orientation
import Microsoft.UI.Xaml.Controls.ScrollBarVisibility
import Microsoft.UI.Xaml.Controls.ScrollMode
import Microsoft.UI.Xaml.Controls.StackLayout
import Microsoft.UI.Xaml.Controls.ScrollViewer as XamlScrollViewer

/**
 * A scrolling list that creates elements only for the items in view (`ItemsRepeater` in a
 * `ScrollViewer`), for lists too long to compose at once.
 *
 * Each realized item is composed by [itemContent] in a composition of its own. With [key], an item
 * keeps its element, and the state remembered inside [itemContent], while the list is reordered or
 * items are inserted and removed around it; keys must be unique. Without [key], items are identified
 * by position. Elements scrolled out of view are recycled for other items, so remembered state does
 * not survive scrolling away: keep lasting per-item state in the items or hoisted above the list.
 * Give the list a bounded size in the scrolling direction (for example `Modifier.height(…)` or a
 * `Star` grid row).
 *
 * ```
 * LazyItems(messages, Modifier.height(400.0), key = { it.id }, spacing = 4.0) { message -> MessageRow(message) }
 * ```
 */
@Composable
fun <T> LazyItems(
    items: List<T>,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    orientation: Orientation = Orientation.Vertical,
    spacing: Double = 0.0,
    itemContent: @Composable (T) -> Unit,
) {
    val parent = rememberCompositionContext()
    val factory = remember { ItemFactory<T>(parent) }
    Leaf({ LazyItemsNode(XamlScrollViewer(), factory) }, modifier) {
        set(orientation) { setOrientation(it) }
        set(spacing) { layout.Spacing = it }
        reconcile { factory.update(items, key, itemContent) }
    }
    DisposableEffect(factory) {
        onDispose { factory.dispose() }
    }
}

internal class LazyItemsNode<T>(private val viewer: XamlScrollViewer, factory: ItemFactory<T>) :
    ElementNode<XamlScrollViewer>(viewer, ChildSlot.None("LazyItems")) {
    val layout = StackLayout()
    private val repeater = ItemsRepeater()

    init {
        repeater.Layout = layout
        repeater.ItemTemplate = factory
        repeater.ItemsSource = factory.source
        viewer.Content = repeater
    }

    fun setOrientation(orientation: Orientation) {
        layout.Orientation = orientation
        val vertical = orientation == Orientation.Vertical
        viewer.VerticalScrollMode = if (vertical) ScrollMode.Enabled else ScrollMode.Disabled
        viewer.VerticalScrollBarVisibility = if (vertical) ScrollBarVisibility.Auto else ScrollBarVisibility.Disabled
        viewer.HorizontalScrollMode = if (vertical) ScrollMode.Disabled else ScrollMode.Enabled
        viewer.HorizontalScrollBarVisibility = if (vertical) ScrollBarVisibility.Disabled else ScrollBarVisibility.Auto
    }
}

/**
 * The repeater's items source: one string id per item. It announces every change as a reset; because
 * it maps ids to indices (`IKeyIndexMapping`), the repeater keeps the elements of ids that are still
 * present instead of recreating them.
 */
internal class KeyedSource : ObservableCollection<Any?>(), IKeyIndexMapping {
    private var indexById = HashMap<String, Int>()

    fun replace(ids: List<String>) {
        Items.Clear()
        for (id in ids) Items.Add(id)
        val index = HashMap<String, Int>(ids.size * 2)
        for (i in ids.indices) index[ids[i]] = i
        indexById = index
        OnCollectionChanged(NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Reset))
    }

    fun indexOf(id: String): Int = indexById[id] ?: -1

    override fun KeyFromIndex(index: Int): String = Items[index] as String

    override fun IndexFromKey(key: String): Int = indexOf(key)
}

/**
 * Creates and recycles the elements of a [LazyItems] list for `ItemsRepeater`. Each element is a host
 * `Grid` whose child is composed from the item with the id it currently shows.
 */
internal class ItemFactory<T>(private val parent: CompositionContext) : IElementFactory {
    val source = KeyedSource()
    private var items: List<T> = emptyList()
    private var content: @Composable (T) -> Unit = {}
    private var ids: List<String> = emptyList()
    private val idsByKey = HashMap<Any, String>()
    private var nextId = 0
    private val hosts = HashMap<UIElement, ItemHost>()
    private val realized = HashMap<ItemHost, String>()
    private val pool = ArrayList<ItemHost>()

    /** Called on every recomposition of the list. */
    fun update(newItems: List<T>, key: ((T) -> Any)?, newContent: @Composable (T) -> Unit) {
        items = newItems
        content = newContent
        val newIds = if (key == null) List(newItems.size) { "#$it" } else idsFor(newItems, key)
        if (newIds != ids) {
            ids = newIds
            source.replace(newIds)
        }
        // Elements the repeater keeps show their item's current data in place.
        for ((host, id) in realized) {
            val index = source.indexOf(id)
            if (index >= 0) show(host, id, index)
        }
    }

    /** Stable string ids for user keys; ids of keys that left the list are dropped. */
    private fun idsFor(newItems: List<T>, key: (T) -> Any): List<String> {
        val present = HashSet<Any>(newItems.size * 2)
        val result = ArrayList<String>(newItems.size)
        for (item in newItems) {
            val k = key(item)
            require(present.add(k)) { "LazyItems keys must be unique; duplicate key: $k" }
            result.add(idsByKey.getOrPut(k) { "k" + nextId++ })
        }
        idsByKey.keys.retainAll(present)
        return result
    }

    override fun GetElement(args: ElementFactoryGetArgs): UIElement {
        val id = args.Data as String
        val host = if (pool.isEmpty()) ItemHost(parent).also { hosts[it.element] = it } else pool.removeAt(pool.size - 1)
        realized[host] = id
        val index = source.indexOf(id)
        if (index >= 0) show(host, id, index)
        return host.element
    }

    override fun RecycleElement(args: ElementFactoryRecycleArgs) {
        val host = hosts[args.Element] ?: return
        realized.remove(host)
        pool.add(host)
    }

    private fun show(host: ItemHost, id: String, index: Int) {
        val item = items[index]
        val itemContent = content
        // Keyed by id: a recycled host must not hand the state of its previous item to the next one.
        host.composition.setContent { key(id) { itemContent(item) } }
    }

    fun dispose() {
        for (host in hosts.values) host.composition.dispose()
        hosts.clear()
        realized.clear()
        pool.clear()
    }
}

/**
 * The element of one realized item and the composition that fills it.
 *
 * The host is a `Grid` (several children allowed) rather than a single-child `Border`: when a
 * recycled host switches to another item, the new item's element is inserted before the old one is
 * removed.
 */
internal class ItemHost(parent: CompositionContext) {
    val element = Grid()
    val composition = Composition(WinUIApplier(ItemRoot(element)), parent)
}

private class ItemRoot(grid: Grid) : Node(ChildSlot.Many(grid.Children))
