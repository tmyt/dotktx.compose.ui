package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import AppKit.NSApplication
import AppKit.NSMenu
import AppKit.NSMenuItem

/** A top-level menu of a [MenuBar]: its [title] on the bar and the [entries] it opens. */
data class MenuBarMenu(val title: String, val entries: List<MenuEntry>)

/**
 * The application's menu bar (`NSApplication.MainMenu`): the application menu followed by [menus].
 * The default menu returns when the composable leaves the composition.
 *
 * Toggle entries show the composed `checked` value: after the user toggles one, the item shows its
 * entry's value again, and the application's decision arrives as new entries.
 *
 * ```
 * MenuBar(listOf(
 *     MenuBarMenu("File", listOf(MenuEntry.Item("Open") { open() })),
 *     MenuBarMenu("View", listOf(MenuEntry.Toggle("Status bar", checked = statusBar) { statusBar = it })),
 * ))
 * ```
 */
@Composable
fun ApplicationScope.MenuBar(menus: List<MenuBarMenu>) {
    val host = this as ApplicationHost
    DisposableEffect(menus) {
        val main = NSMenu()
        main.AutoEnablesItems = false
        main.AddItem(defaultAppMenu(host))
        for (menu in menus) {
            val item = NSMenuItem(menu.title)
            item.Submenu = buildMenu(menu.title, menu.entries)
            main.AddItem(item)
        }
        NSApplication.SharedApplication.MainMenu = main
        onDispose { installDefaultMainMenu(host) }
    }
}
