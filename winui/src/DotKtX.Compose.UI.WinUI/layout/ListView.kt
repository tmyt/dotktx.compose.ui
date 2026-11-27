package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.DataTemplate
import Microsoft.UI.Xaml.DependencyObject
import Microsoft.UI.Xaml.Controls.ContainerContentChangingEventArgs
import Microsoft.UI.Xaml.Controls.ContentControl
import Microsoft.UI.Xaml.Controls.Grid
import Microsoft.UI.Xaml.Controls.ListViewBase
import Microsoft.UI.Xaml.Controls.ListViewSelectionMode
import Microsoft.UI.Xaml.Controls.Panel
import Microsoft.UI.Xaml.Data.ItemIndexRange
import Microsoft.UI.Xaml.Markup.XamlReader
import Microsoft.UI.Xaml.Controls.GridView as XamlGridView
import Microsoft.UI.Xaml.Controls.ListView as XamlListView

/**
 * A vertical list with single selection (`ListView`), controlled by [selectedIndex] (-1 for no
 * selection). Selecting an item calls [onSelectedIndexChange].
 *
 * Only the items in view get elements. Each realized item is composed by [itemContent] in a
 * composition of its own. With [key], an item keeps the state remembered inside [itemContent] while
 * it stays in view, also when items are inserted and removed around it or the list is reordered;
 * keys must be unique. Without [key], items are identified by position. An item scrolled out of view
 * leaves composition, so lasting per-item state belongs in the items or above the list. With [onItemClick], clicking an item also calls it with the item's
 * index. Give the list a bounded height.
 *
 * ```
 * ListView(contacts, selectedIndex = selected, onSelectedIndexChange = { selected = it }, key = { it.id }) { contact ->
 *     TextBlock(contact.name)
 * }
 * ```
 */
@EmitsOneElement
@Composable
fun <T> ListView(
    items: List<T>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    onItemClick: ((Int) -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    ItemsList({ XamlListView() }, "ListView", items, modifier, key, ListSelection.Single(selectedIndex, onSelectedIndexChange), onItemClick, itemContent)
}

/**
 * A vertical list with multiple selection (`ListView` with check boxes), controlled by
 * [selectedIndices]. Selecting or deselecting an item calls [onSelectedIndicesChange] with the new
 * set. Items, keys and element recycling behave as in the single-selection [ListView].
 *
 * ```
 * ListView(files, selectedIndices = checked, onSelectedIndicesChange = { checked = it }) { file -> TextBlock(file.name) }
 * ```
 */
@EmitsOneElement
@Composable
fun <T> ListView(
    items: List<T>,
    selectedIndices: Set<Int>,
    onSelectedIndicesChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    onItemClick: ((Int) -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    ItemsList({ XamlListView() }, "ListView", items, modifier, key, ListSelection.Multiple(selectedIndices, onSelectedIndicesChange), onItemClick, itemContent)
}

/**
 * A vertical list without selection (`ListView`). With [onItemClick], clicking an item calls it with
 * the item's index. Items, keys and element recycling behave as in the single-selection [ListView].
 *
 * ```
 * ListView(commands, onItemClick = { commands[it].run() }) { command -> TextBlock(command.title) }
 * ```
 */
@EmitsOneElement
@Composable
fun <T> ListView(
    items: List<T>,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    onItemClick: ((Int) -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    ItemsList({ XamlListView() }, "ListView", items, modifier, key, ListSelection.None, onItemClick, itemContent)
}

/**
 * A wrapping grid of items with single selection (`GridView`), controlled by [selectedIndex] (-1 for
 * no selection). Items, keys and element recycling behave as in [ListView].
 *
 * ```
 * GridView(photos, selectedIndex = selected, onSelectedIndexChange = { selected = it }, key = { it.id }) { photo ->
 *     Image(photo.uri, Modifier.size(120.0, 120.0))
 * }
 * ```
 */
@EmitsOneElement
@Composable
fun <T> GridView(
    items: List<T>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    onItemClick: ((Int) -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    ItemsList({ XamlGridView() }, "GridView", items, modifier, key, ListSelection.Single(selectedIndex, onSelectedIndexChange), onItemClick, itemContent)
}

/**
 * A wrapping grid of items with multiple selection (`GridView` with check boxes), controlled by
 * [selectedIndices]. Items, keys and element recycling behave as in [ListView].
 *
 * ```
 * GridView(photos, selectedIndices = picked, onSelectedIndicesChange = { picked = it }) { photo -> PhotoTile(photo) }
 * ```
 */
@EmitsOneElement
@Composable
fun <T> GridView(
    items: List<T>,
    selectedIndices: Set<Int>,
    onSelectedIndicesChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    onItemClick: ((Int) -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    ItemsList({ XamlGridView() }, "GridView", items, modifier, key, ListSelection.Multiple(selectedIndices, onSelectedIndicesChange), onItemClick, itemContent)
}

/**
 * A wrapping grid of items without selection (`GridView`). With [onItemClick], clicking an item calls
 * it with the item's index. Items, keys and element recycling behave as in [ListView].
 *
 * ```
 * GridView(apps, onItemClick = { launch(apps[it]) }) { app -> AppTile(app) }
 * ```
 */
@EmitsOneElement
@Composable
fun <T> GridView(
    items: List<T>,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    onItemClick: ((Int) -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    ItemsList({ XamlGridView() }, "GridView", items, modifier, key, ListSelection.None, onItemClick, itemContent)
}

/** The selection of a [ListView] or [GridView] as composed. */
internal sealed class ListSelection {
    object None : ListSelection()
    class Single(val index: Int, val onChange: (Int) -> Unit) : ListSelection()
    class Multiple(val indices: Set<Int>, val onChange: (Set<Int>) -> Unit) : ListSelection()
}

// Not generic in the list type: with DotKt 0.9.31, a lambda passed to an inline function (`Leaf`)
// inside a function whose type parameter is bounded by a CLR class (`<V : ListViewBase>`) compiles to
// a closure type that fails to load (TypeLoadException: violates the constraint of type parameter).
@Composable
private fun <T> ItemsList(
    factory: () -> ListViewBase,
    owner: String,
    items: List<T>,
    modifier: Modifier,
    key: ((T) -> Any)?,
    selection: ListSelection,
    onItemClick: ((Int) -> Unit)?,
    itemContent: @Composable (T) -> Unit,
) {
    val parent = rememberCompositionContext()
    val containers = remember { ContainerItems<T>(parent, owner) }
    val scope = currentRecomposeScope
    Leaf({ ItemsListNode(factory(), owner, containers) }, modifier) {
        set(scope) { single.scope = it; multiple.scope = it }
        set(onItemClick) {
            this.onItemClick = it
            element.IsItemClickEnabled = it != null
        }
        reconcile {
            setSelection(selection)
            update(items, key, itemContent)
        }
    }
    DisposableEffect(containers) {
        onDispose { containers.dispose() }
    }
}

internal class ItemsListNode<T>(
    private val view: ListViewBase,
    owner: String,
    private val containers: ContainerItems<T>,
) : ElementNode<ListViewBase>(view, ChildSlot.None(owner)) {
    var onItemClick: ((Int) -> Unit)? = null
    private var selection: ListSelection = ListSelection.None
    private val source = KeyedSource { containers.compositions.textOf(it) }

    val single = ControlledValue(
        read = { view.SelectedIndex },
        write = { view.SelectedIndex = if (it >= 0 && it < source.Count) it else -1 },
    )

    val multiple = ControlledValue(read = { selectedIndices() }, write = { select(it) })

    init {
        view.ItemTemplate = itemHostTemplate()
        view.ItemsSource = source
        onRelease(view.ContainerContentChanging.subscribe { _, e -> containers.containerChanged(e, idOf(e.Item)) })
        onRelease(view.LayoutUpdated.subscribe { _, _ -> containers.sweep() })
        onRelease(view.SelectionChanged.subscribe { _, _ ->
            when (val s = selection) {
                is ListSelection.Single -> single.userChanged(s.onChange)
                is ListSelection.Multiple -> multiple.userChanged(s.onChange)
                ListSelection.None -> {}
            }
        })
        onRelease(view.ItemClick.subscribe { _, e ->
            val index = idOf(e.ClickedItem)?.let { containers.compositions.indexOf(it) } ?: -1
            if (index >= 0) onItemClick?.invoke(index)
        })
    }

    fun setSelection(value: ListSelection) {
        selection = value
        val mode = when (value) {
            ListSelection.None -> ListViewSelectionMode.None
            is ListSelection.Single -> ListViewSelectionMode.Single
            is ListSelection.Multiple -> ListViewSelectionMode.Multiple
        }
        if (view.SelectionMode != mode) programmatic { view.SelectionMode = mode }
    }

    /**
     * Runs after [setSelection]: the selection is synced against the new items. Changing the items
     * can change WinUI's selection (a selected item is removed); that is not a user change.
     */
    fun update(items: List<T>, key: ((T) -> Any)?, content: @Composable (T) -> Unit) {
        programmatic {
            if (containers.compositions.update(items, key, content)) source.edit(containers.compositions.ids)
        }
        containers.compositions.refresh()
        when (val s = selection) {
            is ListSelection.Single -> single.sync(s.index)
            is ListSelection.Multiple -> multiple.sync(s.indices)
            ListSelection.None -> {}
        }
    }

    private fun programmatic(block: () -> Unit) {
        single.programmatic { multiple.programmatic(block) }
    }

    private fun selectedIndices(): Set<Int> {
        val result = HashSet<Int>()
        for (range in view.SelectedRanges) {
            for (i in range.FirstIndex..range.LastIndex) result.add(i)
        }
        return result
    }

    private fun select(indices: Set<Int>) {
        val count = source.Count
        if (count == 0) return
        view.DeselectRange(ItemIndexRange(0, count.toUInt()))
        var start = -1
        for (i in 0..count) {
            val selected = i < count && i in indices
            if (selected && start < 0) start = i
            if (!selected && start >= 0) {
                view.SelectRange(ItemIndexRange(start, (i - start).toUInt()))
                start = -1
            }
        }
    }
}

/**
 * The item compositions of a list built on `ListViewBase`. The list's item template is an empty
 * `Grid` (see [itemHostTemplate]); a container's template root is the place where the [ItemHost] of
 * the item the container shows is put. When WinUI prepares another container for an item, the item's
 * host moves there with its composition.
 *
 * `ContainerContentChanging` is the hook: `ListViewBase` raises it synchronously when it prepares a
 * container for an item and when it puts a container in its recycle queue. An `IElementFactory` is
 * not available here, `ChoosingItemContainer` only picks the container (its content is filled
 * afterwards from the template), and a `DataTemplateSelector` returns a template rather than the
 * element it creates. The list calls [sweep] after each layout pass, when the containers of the items
 * in view are prepared.
 */
internal class ContainerItems<T>(parent: CompositionContext, owner: String) {
    val compositions = ItemCompositions<T>(owner, parent)
    private val placed = HashMap<Grid, String>()
    private val places = HashMap<String, Grid>()

    fun containerChanged(e: ContainerContentChangingEventArgs, id: String?) {
        val root = e.ItemContainer.ContentTemplateRoot as? Grid ?: return
        if (e.InRecycleQueue || id == null) vacate(root) else place(root, id)
        e.Handled = true
    }

    /** Puts the host of the item with [id] in [root], taking it out of the container it was in. */
    private fun place(root: Grid, id: String) {
        if (placed[root] == id) return
        vacate(root)
        val host = compositions.attach(id)
        places.remove(id)?.let { previous ->
            placed.remove(previous)
            previous.Children.Clear()
        }
        root.Children.Add(host.element)
        placed[root] = id
        places[id] = root
    }

    /** Empties [root]; the item it showed is detached unless another container takes it first. */
    private fun vacate(root: Grid) {
        val id = placed.remove(root) ?: return
        places.remove(id)
        root.Children.Clear()
        compositions.detach(id)
    }

    /**
     * Places the items that the containers of [panel] currently show, for lists that raise no
     * `ContainerContentChanging` for them, and vacates containers that are gone or recycled.
     * [itemId] gives the id of the item a container shows, or null for a recycled container.
     */
    fun scan(panel: Panel?, itemId: (DependencyObject) -> String?) {
        if (panel == null) return
        val seen = HashSet<Grid>()
        val children = panel.Children
        for (i in 0 until children.Count) {
            val container = children[i] as? ContentControl ?: continue
            val root = container.ContentTemplateRoot as? Grid ?: continue
            val id = itemId(container) ?: continue
            seen.add(root)
            place(root, id)
        }
        for (root in placed.keys.toList()) {
            if (root !in seen) vacate(root)
        }
    }

    fun sweep() {
        compositions.sweep()
    }

    fun dispose() {
        compositions.dispose()
        placed.clear()
        places.clear()
    }
}

/**
 * An item template whose root is an empty `Grid`, the place of an item's host. Templates can only be
 * created from markup; the template ignores the item (its id), whose host shows the item instead.
 */
internal fun itemHostTemplate(): DataTemplate =
    XamlReader.Load("<DataTemplate xmlns=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\"><Grid/></DataTemplate>") as DataTemplate
