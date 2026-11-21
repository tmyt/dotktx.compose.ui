package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import Microsoft.UI.Xaml.ElementFactoryGetArgs
import Microsoft.UI.Xaml.ElementFactoryRecycleArgs
import Microsoft.UI.Xaml.IElementFactory
import Microsoft.UI.Xaml.UIElement
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
 * Creates and recycles the elements of a [LazyItems] list for `ItemsRepeater`. Each element is a host
 * `Grid` whose child is composed from the item with the id it currently shows.
 */
internal class ItemFactory<T>(private val parent: CompositionContext) : IElementFactory {
    val source = KeyedSource()
    private val compositions = ItemCompositions<T>("LazyItems")
    private val hosts = HashMap<UIElement, ItemHost>()
    private val pool = ArrayList<ItemHost>()

    /** Called on every recomposition of the list. */
    fun update(newItems: List<T>, key: ((T) -> Any)?, newContent: @Composable (T) -> Unit) {
        if (compositions.update(newItems, key, newContent)) source.replace(compositions.ids)
        compositions.refresh()
    }

    override fun GetElement(args: ElementFactoryGetArgs): UIElement {
        val id = args.Data as String
        val host = if (pool.isEmpty()) ItemHost(parent).also { hosts[it.element] = it } else pool.removeAt(pool.size - 1)
        compositions.bind(host, id)
        return host.element
    }

    override fun RecycleElement(args: ElementFactoryRecycleArgs) {
        val host = hosts[args.Element] ?: return
        compositions.unbind(host)
        pool.add(host)
    }

    fun dispose() {
        for (host in hosts.values) host.composition.dispose()
        hosts.clear()
        compositions.clear()
        pool.clear()
    }
}
