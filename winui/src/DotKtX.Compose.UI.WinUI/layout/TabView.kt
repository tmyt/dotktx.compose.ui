package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.UIElement
import Microsoft.UI.Xaml.Controls.Border
import Microsoft.UI.Xaml.Controls.TabViewItem
import Microsoft.UI.Xaml.Controls.TabView as XamlTabView

/** One tab of a [TabView]. */
data class TabItem(val header: String, val closable: Boolean = true)

/**
 * A row of tabs over a content area (`TabView`), controlled by [selectedIndex].
 *
 * Choosing a tab calls [onSelect]. With [onClose] each closable tab has a close button that calls it
 * with the tab's index; with [onAdd] the add button is shown. [content] is the content of the
 * selected tab (switch on [selectedIndex] inside it) and must emit at most one element.
 *
 * ```
 * TabView(tabs, selectedIndex = current, onSelect = { current = it }, onClose = { docs.removeAt(it) }) {
 *     DocumentEditor(docs[current])
 * }
 * ```
 */
@Composable
fun TabView(
    tabs: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onClose: ((Int) -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scope = currentRecomposeScope
    Container({ tabViewNode(XamlTabView()) }, modifier, {
        set(scope) { selection.scope = it }
        set(onSelect) { this.onSelect = it }
        set(onClose) { this.onClose = it }
        set(onAdd) { this.onAdd = it; element.IsAddTabButtonVisible = it != null }
        set(tabs) { setTabs(it) }
        reconcile {
            selection.sync(selectedIndex)
            contentHost.place()
        }
    }, content)
}

private fun tabViewNode(view: XamlTabView): TabViewNode = TabViewNode(view, TabContentHost(view))

/**
 * WinUI shows the content of the selected `TabViewItem`, so the single content element is moved into
 * whichever item is selected.
 *
 * TabView copies the selected item's `Content` when the selection changes and does not follow later
 * changes of it. Each item therefore gets a host `Border` as its content up front, and the element
 * moves between the hosts' `Child`, which the displayed host shows immediately.
 */
internal class TabContentHost(private val view: XamlTabView) {
    private var element: UIElement? = null
    private var holder: Border? = null

    fun set(content: UIElement?) {
        element = content
        place()
    }

    fun place() {
        val target = (view.SelectedItem as? TabViewItem)?.Content as? Border
        if (holder !== target) {
            holder?.Child = null
            holder = target
        }
        target?.Child = element
    }

    fun forgetHolder() {
        holder?.Child = null
        holder = null
    }
}

internal class TabViewNode(private val view: XamlTabView, val contentHost: TabContentHost) :
    ElementNode<XamlTabView>(view, ChildSlot.Single("TabView") { contentHost.set(it) }) {
    var onSelect: (Int) -> Unit = {}
    var onClose: ((Int) -> Unit)? = null
    var onAdd: (() -> Unit)? = null

    val selection = ControlledValue(read = { view.SelectedIndex }, write = { view.SelectedIndex = it })

    init {
        onRelease(view.SelectionChanged.subscribe { _, _ ->
            contentHost.place()
            selection.userChanged(onSelect)
        })
        onRelease(view.TabCloseRequested.subscribe { _, e ->
            val index = view.TabItems.IndexOf(e.Tab)
            if (index >= 0) onClose?.invoke(index)
        })
        onRelease(view.AddTabButtonClick.subscribe { _, _ -> onAdd?.invoke() })
    }

    /** Replacing the tabs clears the selection; that is not a user change, and sync restores it. */
    fun setTabs(tabs: List<TabItem>) {
        selection.programmatic {
            contentHost.forgetHolder()
            view.TabItems.Clear()
            for (tab in tabs) {
                val item = TabViewItem()
                item.Header = tab.header
                item.IsClosable = tab.closable && onClose != null
                item.Content = Border()
                view.TabItems.Add(item)
            }
        }
    }
}
