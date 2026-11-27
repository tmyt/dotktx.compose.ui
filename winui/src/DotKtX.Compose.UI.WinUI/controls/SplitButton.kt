package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import dotktx.compose.ui.annotations.AtMostOneElementContent
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.Controls.SplitButton as XamlSplitButton
import Microsoft.UI.Xaml.Controls.ToggleSplitButton as XamlToggleSplitButton

/**
 * A button with a main part and an arrow that opens a menu (`SplitButton`). The main part calls
 * [onClick]; the arrow opens [menu]. [content] is the main part's label and must emit exactly one
 * element.
 *
 * ```
 * SplitButton(
 *     onClick = { paste(PasteMode.Default) },
 *     menu = listOf(MenuEntry.Item("Paste as text") { paste(PasteMode.Text) }),
 * ) { TextBlock("Paste") }
 * ```
 */
@EmitsOneElement
@Composable
fun SplitButton(
    onClick: () -> Unit,
    menu: List<MenuEntry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @AtMostOneElementContent content: @Composable () -> Unit,
) {
    Container({ SplitButtonNode(XamlSplitButton()) }, modifier, {
        set(onClick) { this.onClick = it }
        set(menu) { element.Flyout = menuFlyout(it) }
        set(enabled) { element.IsEnabled = it }
    }, content)
}

/** `Click` is raised by the main part only; the arrow part opens the flyout without it. */
internal class SplitButtonNode(button: XamlSplitButton) :
    ElementNode<XamlSplitButton>(button, ChildSlot.Single("SplitButton") { button.Content = it }) {
    var onClick: () -> Unit = {}

    init {
        onRelease(button.Click.subscribe { _, _ -> onClick() })
    }
}

/**
 * A [SplitButton] whose main part stays pressed (`ToggleSplitButton`), controlled by [checked].
 * Clicking the main part calls [onCheckedChange]; the arrow opens [menu], typically to pick what the
 * button turns on. [content] is the main part's label and must emit exactly one element.
 *
 * ```
 * ToggleSplitButton(
 *     checked = bullets,
 *     onCheckedChange = { bullets = it },
 *     menu = listOf(MenuEntry.Item("Numbers") { style = ListStyle.Numbers; bullets = true }),
 * ) { TextBlock("List") }
 * ```
 */
@EmitsOneElement
@Composable
fun ToggleSplitButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    menu: List<MenuEntry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @AtMostOneElementContent content: @Composable () -> Unit,
) {
    val scope = currentRecomposeScope
    Container({ ToggleSplitButtonNode(XamlToggleSplitButton()) }, modifier, {
        set(scope) { input.scope = it }
        set(onCheckedChange) { this.onCheckedChange = it }
        set(menu) { element.Flyout = menuFlyout(it) }
        set(enabled) { element.IsEnabled = it }
        reconcile { input.sync(checked) }
    }, content)
}

/**
 * The main part toggles `IsChecked` and then raises `Click`; the toggle is the user's action, so only
 * `IsCheckedChanged` is reported, which is raised synchronously with the change.
 */
internal class ToggleSplitButtonNode(button: XamlToggleSplitButton) :
    ElementNode<XamlToggleSplitButton>(button, ChildSlot.Single("ToggleSplitButton") { button.Content = it }) {
    var onCheckedChange: (Boolean) -> Unit = {}

    val input = ControlledValue(read = { button.IsChecked }, write = { button.IsChecked = it })

    init {
        onRelease(button.IsCheckedChanged.subscribe { _, _ -> input.userChanged(onCheckedChange) })
    }
}
