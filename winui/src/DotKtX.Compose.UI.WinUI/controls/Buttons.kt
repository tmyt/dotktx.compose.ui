package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.Primitives.RepeatButton as XamlRepeatButton
import Microsoft.UI.Xaml.Controls.Primitives.ToggleButton as XamlToggleButton

/**
 * A button that stays pressed (`ToggleButton`), controlled by [checked]. [content] must emit exactly
 * one element, which becomes the button's content.
 *
 * ```
 * ToggleButton(checked = bold, onCheckedChange = { bold = it }) { TextBlock("Bold") }
 * ```
 */
@Composable
fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scope = currentRecomposeScope
    Container({ ToggleButtonNode(XamlToggleButton()) }, modifier, {
        set(scope) { input.scope = it }
        set(onCheckedChange) { this.onCheckedChange = it }
        set(enabled) { element.IsEnabled = it }
        reconcile { input.sync(checked) }
    }, content)
}

internal class ToggleButtonNode(button: XamlToggleButton) :
    ElementNode<XamlToggleButton>(button, ChildSlot.Single("ToggleButton") { button.Content = it }) {
    var onCheckedChange: (Boolean) -> Unit = {}

    val input = ControlledValue(read = { button.IsChecked == true }, write = { button.IsChecked = it })

    init {
        onRelease(button.Checked.subscribe { _, _ -> input.userChanged(onCheckedChange) })
        onRelease(button.Unchecked.subscribe { _, _ -> input.userChanged(onCheckedChange) })
    }
}

/**
 * A button that calls [onClick] repeatedly while it is held down (`RepeatButton`): once when pressed,
 * then every [intervalMillis] after an initial [delayMillis]. `null` keeps the control's default
 * timing. [content] must emit exactly one element.
 *
 * ```
 * RepeatButton(onClick = { zoom++ }) { TextBlock("+") }
 * ```
 */
@Composable
fun RepeatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    delayMillis: Int? = null,
    intervalMillis: Int? = null,
    content: @Composable () -> Unit,
) {
    Container({ ButtonNode(XamlRepeatButton(), "RepeatButton") }, modifier, {
        set(onClick) { this.onClick = it }
        set(enabled) { element.IsEnabled = it }
        set(delayMillis) { if (it == null) element.ClearValue(XamlRepeatButton.DelayProperty) else element.Delay = it }
        set(intervalMillis) { if (it == null) element.ClearValue(XamlRepeatButton.IntervalProperty) else element.Interval = it }
    }, content)
}
