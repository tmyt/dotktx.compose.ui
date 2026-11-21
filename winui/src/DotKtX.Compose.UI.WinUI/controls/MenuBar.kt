package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import Microsoft.UI.Xaml.Controls.MenuBarItem
import Microsoft.UI.Xaml.Controls.MenuBar as XamlMenuBar

/** A top-level menu of a [MenuBar]: its [title] on the bar and the [entries] it opens. */
data class MenuBarMenu(val title: String, val entries: List<MenuEntry>)

/**
 * A row of top-level menus (`MenuBar`), usually at the top of a window.
 *
 * Each menu's items are built from its [MenuEntry] list as in [DropDownButton] and
 * [Modifier.contextMenu]. A menu's items are rebuilt when its entries change. Toggle entries show
 * the composed `checked` value: after the user toggles one, the item shows its entry's value again,
 * and the application's decision arrives as new entries.
 *
 * ```
 * MenuBar(listOf(
 *     MenuBarMenu("File", listOf(MenuEntry.Item("Open", Symbol.OpenFile) { open() }, MenuEntry.Item("Exit") { exit() })),
 *     MenuBarMenu("View", listOf(MenuEntry.Toggle("Status bar", checked = statusBar) { statusBar = it })),
 * ))
 * ```
 */
@Composable
fun MenuBar(menus: List<MenuBarMenu>, modifier: Modifier = Modifier) {
    Leaf({ MenuBarNode(XamlMenuBar()) }, modifier) {
        set(menus) { setMenus(it) }
    }
}

internal class MenuBarNode(private val bar: XamlMenuBar) : ElementNode<XamlMenuBar>(bar, ChildSlot.None("MenuBar")) {
    private var titles: List<String> = emptyList()
    private val entries = ArrayList<List<MenuEntry>?>()

    /** Recreates the top-level items when the titles change; otherwise refills only the menus whose entries changed. */
    fun setMenus(menus: List<MenuBarMenu>) {
        val newTitles = menus.map { it.title }
        if (newTitles != titles) {
            bar.Items.Clear()
            entries.clear()
            for (menu in menus) {
                val item = MenuBarItem()
                item.Title = menu.title
                bar.Items.Add(item)
                entries.add(null)
            }
            titles = newTitles
        }
        for (i in menus.indices) {
            if (entries[i] != menus[i].entries) {
                fillMenuItems(bar.Items[i].Items, menus[i].entries)
                entries[i] = menus[i].entries
            }
        }
    }
}
