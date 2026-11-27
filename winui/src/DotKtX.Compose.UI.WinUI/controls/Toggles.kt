package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import dotktx.compose.ui.annotations.AtMostOneElementContent
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.Controls.CheckBox as XamlCheckBox
import Microsoft.UI.Xaml.Controls.ToggleSwitch as XamlToggleSwitch

/**
 * A check box (`CheckBox`), controlled by [checked]. [content] is the label and must emit at most one
 * element.
 *
 * ```
 * CheckBox(checked = agreed, onCheckedChange = { agreed = it }) { TextBlock("I agree") }
 * ```
 */
@EmitsOneElement
@Composable
fun CheckBox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @AtMostOneElementContent content: @Composable () -> Unit = {},
) {
    val scope = currentRecomposeScope
    Container({ CheckBoxNode(XamlCheckBox()) }, modifier, {
        set(scope) { input.scope = it }
        set(onCheckedChange) { this.onCheckedChange = it }
        set(enabled) { element.IsEnabled = it }
        reconcile { input.sync(checked) }
    }, content)
}

internal class CheckBoxNode(box: XamlCheckBox) :
    ElementNode<XamlCheckBox>(box, ChildSlot.Single("CheckBox") { box.Content = it }) {
    var onCheckedChange: (Boolean) -> Unit = {}

    val input = ControlledValue(read = { box.IsChecked == true }, write = { box.IsChecked = it })

    init {
        onRelease(box.Checked.subscribe { _, _ -> input.userChanged(onCheckedChange) })
        onRelease(box.Unchecked.subscribe { _, _ -> input.userChanged(onCheckedChange) })
    }
}

/**
 * An on/off switch (`ToggleSwitch`), controlled by [isOn]. [header] is shown above the switch.
 *
 * ```
 * ToggleSwitch(isOn = wifi, onToggle = { wifi = it }, header = "Wi-Fi")
 * ```
 */
@EmitsOneElement
@Composable
fun ToggleSwitch(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    header: String = "",
) {
    val scope = currentRecomposeScope
    Leaf({ ToggleSwitchNode(XamlToggleSwitch()) }, modifier) {
        set(scope) { input.scope = it }
        set(onToggle) { this.onToggle = it }
        set(enabled) { element.IsEnabled = it }
        set(header) { if (it.isEmpty()) element.ClearValue(XamlToggleSwitch.HeaderProperty) else element.Header = it }
        reconcile { input.sync(isOn) }
    }
}

internal class ToggleSwitchNode(toggle: XamlToggleSwitch) : ElementNode<XamlToggleSwitch>(toggle, ChildSlot.None("ToggleSwitch")) {
    var onToggle: (Boolean) -> Unit = {}

    val input = ControlledValue(read = { toggle.IsOn }, write = { toggle.IsOn = it })

    init {
        onRelease(toggle.Toggled.subscribe { _, _ -> input.userChanged(onToggle) })
    }
}
