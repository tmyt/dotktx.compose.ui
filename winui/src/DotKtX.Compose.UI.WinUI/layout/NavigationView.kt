package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.NavigationViewBackButtonVisible
import Microsoft.UI.Xaml.Controls.NavigationViewItem
import Microsoft.UI.Xaml.Controls.NavigationViewPaneDisplayMode
import Microsoft.UI.Xaml.Controls.NavigationView as XamlNavigationView

/** One entry of a [NavigationView] menu. [key] identifies it in `selectedKey` and `onSelect`. */
data class NavigationItem(val key: String, val label: String, val icon: Icon? = null)

/**
 * The top-level navigation of an app (`NavigationView`): a menu of [items] and a content area.
 *
 * The selection is controlled by [selectedKey]; choosing an item calls [onSelect], and [content]
 * usually shows the page for the selected key. With [onBack] the back button is shown and calls it.
 * [content] must emit at most one element.
 *
 * The pane is left to WinUI while [paneOpen] is null. Otherwise it is controlled by [paneOpen], and
 * opening or closing it by hand calls [onPaneOpenChange]. [paneToggleButton] hides the pane's own
 * toggle button, for example when a [TitleBar] toggles the pane instead.
 *
 * ```
 * NavigationView(items, selectedKey = page, onSelect = { page = it }) {
 *     when (page) { "home" -> HomePage(); "settings" -> SettingsPage() }
 * }
 * ```
 */
@Composable
fun NavigationView(
    items: List<NavigationItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    paneTitle: String = "",
    paneDisplayMode: NavigationViewPaneDisplayMode = NavigationViewPaneDisplayMode.Auto,
    onBack: (() -> Unit)? = null,
    paneOpen: Boolean? = null,
    onPaneOpenChange: (Boolean) -> Unit = {},
    paneToggleButton: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scope = currentRecomposeScope
    Container({ NavigationViewNode(XamlNavigationView()) }, modifier, {
        set(scope) { selection.scope = it; pane.scope = it }
        set(onPaneOpenChange) { this.onPaneOpenChange = it }
        set(paneToggleButton) { element.IsPaneToggleButtonVisible = it }
        set(onSelect) { this.onSelect = it }
        set(onBack) {
            this.onBack = it
            element.IsBackButtonVisible = if (it != null) NavigationViewBackButtonVisible.Visible else NavigationViewBackButtonVisible.Collapsed
            element.IsBackEnabled = it != null
        }
        set(paneTitle) { element.PaneTitle = it }
        set(paneDisplayMode) { element.PaneDisplayMode = it }
        set(items) { setItems(it) }
        reconcile {
            selection.sync(selectedKey)
            paneControlled = paneOpen != null
            if (paneOpen != null) pane.sync(paneOpen)
        }
    }, content)
}

internal class NavigationViewNode(private val view: XamlNavigationView) :
    ElementNode<XamlNavigationView>(view, ChildSlot.Single("NavigationView") { view.Content = it }) {
    var onSelect: (String) -> Unit = {}
    var onBack: (() -> Unit)? = null
    var onPaneOpenChange: (Boolean) -> Unit = {}
    var paneControlled = false
    private val itemsByKey = HashMap<String, NavigationViewItem>()

    val selection = ControlledValue<String?>(
        read = { (view.SelectedItem as? NavigationViewItem)?.Tag as? String },
        write = { key -> view.SelectedItem = key?.let { itemsByKey[it] } },
    )

    val pane = ControlledValue(read = { view.IsPaneOpen }, write = { view.IsPaneOpen = it })

    init {
        view.IsSettingsVisible = false
        onRelease(view.PaneOpened.subscribe { _, _ -> if (paneControlled) pane.userChanged(onPaneOpenChange) })
        onRelease(view.PaneClosed.subscribe { _, _ -> if (paneControlled) pane.userChanged(onPaneOpenChange) })
        onRelease(view.SelectionChanged.subscribe { _, _ -> selection.userChanged { key -> if (key != null) onSelect(key) } })
        onRelease(view.BackRequested.subscribe { _, _ -> onBack?.invoke() })
    }

    /** Replacing the menu clears the selection; that is not a user change, and sync restores it. */
    fun setItems(items: List<NavigationItem>) {
        selection.programmatic {
            view.MenuItems.Clear()
            itemsByKey.clear()
            for (item in items) {
                val entry = NavigationViewItem()
                entry.Content = item.label
                entry.Tag = item.key
                if (item.icon != null) entry.Icon = item.icon.toIconElement()
                view.MenuItems.Add(entry)
                itemsByKey[item.key] = entry
            }
        }
    }
}
