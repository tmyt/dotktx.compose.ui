package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import dotktx.compose.ui.annotations.AtMostOneElementContent
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.FrameworkElement
import Microsoft.UI.Xaml.UIElement
import Microsoft.UI.Xaml.Controls.MenuFlyout
import Microsoft.UI.Xaml.Controls.MenuFlyoutItem
import Microsoft.UI.Xaml.Controls.MenuFlyoutItemBase
import Microsoft.UI.Xaml.Controls.MenuFlyoutSeparator
import Microsoft.UI.Xaml.Controls.MenuFlyoutSubItem
import Microsoft.UI.Xaml.Controls.ToggleMenuFlyoutItem
import System.Collections.Generic.IList
import Microsoft.UI.Xaml.Controls.DropDownButton as XamlDropDownButton

/** An entry of a menu ([Modifier.contextMenu], [DropDownButton], [MenuBar]). */
sealed class MenuEntry {
    /** A command. */
    data class Item(
        val text: String,
        val icon: Icon? = null,
        val enabled: Boolean = true,
        val onClick: () -> Unit,
    ) : MenuEntry()

    /** An on/off entry with a check mark, controlled by [checked]. */
    data class Toggle(
        val text: String,
        val checked: Boolean,
        val enabled: Boolean = true,
        val onCheckedChange: (Boolean) -> Unit,
    ) : MenuEntry()

    /** A nested menu. */
    data class SubMenu(val text: String, val entries: List<MenuEntry>, val icon: Icon? = null) : MenuEntry()

    /** A dividing line. */
    object Separator : MenuEntry()
}

/**
 * A menu that opens on right-click, Shift+F10 or the context-menu key (`UIElement.ContextFlyout`).
 *
 * ```
 * TextBlock(note.title, Modifier.contextMenu(listOf(
 *     MenuEntry.Item("Rename", Icon.Symbol(Symbol.Rename)) { renaming = note },
 *     MenuEntry.Separator,
 *     MenuEntry.Item("Delete", Icon.Symbol(Symbol.Delete)) { notes.remove(note) },
 * )))
 * ```
 */
fun Modifier.contextMenu(entries: List<MenuEntry>): Modifier = this then ContextMenuModifier(entries)

private data class ContextMenuModifier(val entries: List<MenuEntry>) : ElementModifier {
    override val key: Any get() = ContextMenuModifier::class

    override fun apply(element: FrameworkElement) {
        element.ContextFlyout = menuFlyout(entries)
    }

    override fun reset(element: FrameworkElement) {
        element.ClearValue(UIElement.ContextFlyoutProperty)
    }
}

/**
 * A button that opens a menu (`DropDownButton`). [content] is the button's label and must emit
 * exactly one element.
 *
 * ```
 * DropDownButton(listOf(MenuEntry.Item("PNG") { export("png") }, MenuEntry.Item("SVG") { export("svg") })) {
 *     TextBlock("Export")
 * }
 * ```
 */
@EmitsOneElement
@Composable
fun DropDownButton(
    menu: List<MenuEntry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @AtMostOneElementContent content: @Composable () -> Unit,
) {
    Container({ dropDownNode(XamlDropDownButton()) }, modifier, {
        set(menu) { element.Flyout = menuFlyout(it) }
        set(enabled) { element.IsEnabled = it }
    }, content)
}

private fun dropDownNode(button: XamlDropDownButton): ElementNode<XamlDropDownButton> =
    ElementNode(button, ChildSlot.Single("DropDownButton") { button.Content = it })

/**
 * Builds a `MenuFlyout` whose items are created each time it opens, so toggle entries always show
 * the state the application last composed, even after the user toggled one and the application
 * rejected the change.
 */
internal fun menuFlyout(entries: List<MenuEntry>): MenuFlyout {
    val flyout = MenuFlyout()
    // The subscription lives as long as the flyout, which is replaced when the entries change.
    flyout.Opening.subscribe { _, _ -> fillMenuItems(flyout.Items, entries) }
    return flyout
}

/** Replaces [items] with menu items built from [entries]. */
internal fun fillMenuItems(items: IList<MenuFlyoutItemBase>, entries: List<MenuEntry>) {
    items.Clear()
    for (entry in entries) items.Add(menuItem(entry))
}

private fun menuItem(entry: MenuEntry): MenuFlyoutItemBase = when (entry) {
    is MenuEntry.Item -> MenuFlyoutItem().also { item ->
        item.Text = entry.text
        item.IsEnabled = entry.enabled
        if (entry.icon != null) item.Icon = entry.icon.toIconElement()
        item.Click.subscribe { _, _ -> entry.onClick() }
    }
    is MenuEntry.Toggle -> ToggleMenuFlyoutItem().also { item ->
        item.Text = entry.text
        item.IsEnabled = entry.enabled
        item.IsChecked = entry.checked
        item.Click.subscribe { _, _ ->
            entry.onCheckedChange(item.IsChecked)
            // An item shows the entry it was built from; the application's decision arrives as new
            // entries, which rebuild the items. A rejected toggle therefore does not stay checked.
            item.IsChecked = entry.checked
        }
    }
    is MenuEntry.SubMenu -> MenuFlyoutSubItem().also { item ->
        item.Text = entry.text
        if (entry.icon != null) item.Icon = entry.icon.toIconElement()
        fillMenuItems(item.Items, entry.entries)
    }
    MenuEntry.Separator -> MenuFlyoutSeparator()
}
