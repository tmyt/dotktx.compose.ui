package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.ComboBox as XamlComboBox

/**
 * A drop-down list of strings (`ComboBox`), controlled by [selectedIndex] (-1 for no selection).
 *
 * ```
 * ComboBox(items = sizes, selectedIndex = size, onSelectedIndexChange = { size = it })
 * ```
 */
@Composable
fun ComboBox(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    val scope = currentRecomposeScope
    Leaf({ ComboBoxNode(XamlComboBox()) }, modifier) {
        set(scope) { input.scope = it }
        set(onSelectedIndexChange) { this.onSelectedIndexChange = it }
        set(enabled) { element.IsEnabled = it }
        set(placeholder) { element.PlaceholderText = it }
        // Replacing the items clears the selection; that is not a user change, and sync restores it below.
        set(items) { list ->
            input.programmatic {
                element.Items.Clear()
                for (item in list) element.Items.Add(item)
            }
        }
        reconcile { input.sync(selectedIndex) }
    }
}

internal class ComboBoxNode(box: XamlComboBox) : ElementNode<XamlComboBox>(box, ChildSlot.None("ComboBox")) {
    var onSelectedIndexChange: (Int) -> Unit = {}

    val input = ControlledValue(read = { box.SelectedIndex }, write = { box.SelectedIndex = it })

    init {
        onRelease(box.SelectionChanged.subscribe { _, _ -> input.userChanged(onSelectedIndexChange) })
    }
}
