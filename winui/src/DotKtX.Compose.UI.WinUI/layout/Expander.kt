package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import dotktx.compose.ui.annotations.AtMostOneElementContent
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.Controls.Expander as XamlExpander

/**
 * A header that shows or hides [content] below it (`Expander`), controlled by [expanded].
 * [content] must emit at most one element.
 *
 * ```
 * Expander("Advanced", expanded = advanced, onExpandedChange = { advanced = it }) { AdvancedSettings() }
 * ```
 */
@EmitsOneElement
@Composable
fun Expander(
    header: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    @AtMostOneElementContent content: @Composable () -> Unit,
) {
    val scope = currentRecomposeScope
    Container({ ExpanderNode(XamlExpander()) }, modifier, {
        set(scope) { input.scope = it }
        set(onExpandedChange) { this.onExpandedChange = it }
        set(header) { element.Header = it }
        reconcile { input.sync(expanded) }
    }, content)
}

internal class ExpanderNode(expander: XamlExpander) :
    ElementNode<XamlExpander>(expander, ChildSlot.Single("Expander") { expander.Content = it }) {
    var onExpandedChange: (Boolean) -> Unit = {}

    val input = ControlledValue(read = { expander.IsExpanded }, write = { expander.IsExpanded = it })

    init {
        onRelease(expander.Expanding.subscribe { _, _ -> input.userChanged(onExpandedChange) })
        onRelease(expander.Collapsed.subscribe { _, _ -> input.userChanged(onExpandedChange) })
    }
}
