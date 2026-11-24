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
        onRelease(repeater.LayoutUpdated.subscribe { _, _ -> factory.sweep() })
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
 * Creates and recycles the elements of a [LazyItems] list for `ItemsRepeater`. An item's element is
 * the element of its [ItemHost], so the repeater can recycle it and ask for the item again within a
 * layout pass without the item losing its composition. The list calls [sweep] after each layout pass.
 */
internal class ItemFactory<T>(parent: CompositionContext) : IElementFactory {
    val source = KeyedSource()
    private val compositions = ItemCompositions<T>("LazyItems", parent)
    private val shown = HashMap<UIElement, String>()

    /** Called on every recomposition of the list. */
    fun update(newItems: List<T>, key: ((T) -> Any)?, newContent: @Composable (T) -> Unit) {
        if (compositions.update(newItems, key, newContent)) source.replace(compositions.ids)
        compositions.refresh()
    }

    override fun GetElement(args: ElementFactoryGetArgs): UIElement {
        val id = args.Data as String
        val host = compositions.attach(id)
        shown[host.element] = id
        return host.element
    }

    override fun RecycleElement(args: ElementFactoryRecycleArgs) {
        val id = shown.remove(args.Element) ?: return
        compositions.detach(id)
    }

    fun sweep() {
        compositions.sweep()
    }

    fun dispose() {
        compositions.dispose()
        shown.clear()
    }
}
