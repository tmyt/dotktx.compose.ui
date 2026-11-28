package dotktx.compose.ui.appkit

import AppKit.NSCellStateValue
import AppKit.NSMenu
import AppKit.NSMenuItem

/** An entry of a menu ([Modifier.contextMenu], [MenuBar]). */
sealed class MenuEntry {
    /** A command. */
    data class Item(val text: String, val enabled: Boolean = true, val onClick: () -> Unit) : MenuEntry()

    /** An on/off entry with a check mark, controlled by [checked]. */
    data class Toggle(
        val text: String,
        val checked: Boolean,
        val enabled: Boolean = true,
        val onCheckedChange: (Boolean) -> Unit,
    ) : MenuEntry()

    /** A nested menu. */
    data class SubMenu(val text: String, val entries: List<MenuEntry>) : MenuEntry()

    /** A dividing line. */
    object Separator : MenuEntry()
}

/** Builds a menu titled [title] whose items come from [entries]. */
internal fun buildMenu(title: String, entries: List<MenuEntry>): NSMenu {
    val menu = NSMenu(title)
    fillMenu(menu, entries)
    return menu
}

/** Replaces the items of [menu] with items built from [entries]. */
internal fun fillMenu(menu: NSMenu, entries: List<MenuEntry>) {
    menu.AutoEnablesItems = false
    menu.RemoveAllItems()
    for (entry in entries) {
        menu.AddItem(menuItem(entry))
    }
}

private fun menuItem(entry: MenuEntry): NSMenuItem = when (entry) {
    is MenuEntry.Item -> {
        val item = NSMenuItem()
        item.Title = entry.text
        item.Enabled = entry.enabled
        item.Activated.subscribe { _, _ -> entry.onClick() }
        item
    }
    is MenuEntry.Toggle -> {
        val item = NSMenuItem()
        item.Title = entry.text
        item.Enabled = entry.enabled
        item.State = if (entry.checked) NSCellStateValue.On else NSCellStateValue.Off
        item.Activated.subscribe { _, _ ->
            entry.onCheckedChange(!entry.checked)
            // The application's decision arrives as new entries; until then show the composed value.
            item.State = if (entry.checked) NSCellStateValue.On else NSCellStateValue.Off
        }
        item
    }
    is MenuEntry.SubMenu -> {
        val item = NSMenuItem()
        item.Title = entry.text
        item.Submenu = buildMenu(entry.text, entry.entries)
        item
    }
    MenuEntry.Separator -> NSMenuItem.SeparatorItem
}

/**
 * A menu that opens on right-click (`NSView.Menu`).
 *
 * ```
 * Label(note.title, Modifier.contextMenu(listOf(
 *     MenuEntry.Item("Rename") { renaming = note },
 *     MenuEntry.Separator,
 *     MenuEntry.Item("Delete") { notes.remove(note) },
 * )))
 * ```
 */
fun Modifier.contextMenu(entries: List<MenuEntry>): Modifier = this then ContextMenuModifier(entries)

private data class ContextMenuModifier(val entries: List<MenuEntry>) : ElementModifier {
    override val key: Any get() = ContextMenuModifier::class

    override fun apply(node: ElementNode<*>) {
        node.view.Menu = buildMenu("", entries)
    }

    override fun reset(node: ElementNode<*>) {
        node.view.Menu = null
    }
}
