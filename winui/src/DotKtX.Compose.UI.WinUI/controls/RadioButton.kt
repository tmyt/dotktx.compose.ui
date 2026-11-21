package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.RadioButton as XamlRadioButton

/**
 * A radio button (`RadioButton`). [selected] comes from the application, and choosing the button
 * calls [onClick]; the application selects it by updating its state, which also deselects the others.
 * [content] is the label and must emit at most one element.
 *
 * ```
 * for (option in options) {
 *     RadioButton(selected = option == choice, onClick = { choice = option }) { TextBlock(option) }
 * }
 * ```
 */
@Composable
fun RadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    val scope = currentRecomposeScope
    Container({ RadioButtonNode(XamlRadioButton()) }, modifier, {
        set(scope) { input.scope = it }
        set(onClick) { this.onClick = it }
        set(enabled) { element.IsEnabled = it }
        reconcile { input.sync(selected) }
    }, content)
}

internal class RadioButtonNode(button: XamlRadioButton) :
    ElementNode<XamlRadioButton>(button, ChildSlot.Single("RadioButton") { button.Content = it }) {
    var onClick: () -> Unit = {}

    val input = ControlledValue(read = { button.IsChecked == true }, write = { button.IsChecked = it })

    init {
        // A group of its own: WinUI would otherwise deselect sibling radio buttons by itself, and
        // which button is selected is decided by the composition.
        button.GroupName = "dotktx.compose.ui.winui.radio." + nextGroupId++
        onRelease(button.Checked.subscribe { _, _ -> input.userChanged { if (it) onClick() } })
    }

    private companion object {
        var nextGroupId = 0
    }
}
