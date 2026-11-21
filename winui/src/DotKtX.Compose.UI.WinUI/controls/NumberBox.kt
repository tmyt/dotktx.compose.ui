package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.NumberBoxSpinButtonPlacementMode
import Microsoft.UI.Xaml.Controls.NumberBox as XamlNumberBox

/**
 * Numeric input (`NumberBox`), controlled by [value] (`NaN` when empty). The value is committed when
 * the user presses Enter, leaves the box or uses the spin buttons, and then [onValueChange] is called.
 * Input outside [minimum]..[maximum] is clamped by the control.
 *
 * ```
 * NumberBox(value = quantity, onValueChange = { quantity = it }, minimum = 1.0, maximum = 99.0)
 * ```
 */
@Composable
fun NumberBox(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    minimum: Double = Double.NEGATIVE_INFINITY,
    maximum: Double = Double.POSITIVE_INFINITY,
    step: Double = 1.0,
    spinButtons: Boolean = true,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    val scope = currentRecomposeScope
    Leaf({ NumberBoxNode(XamlNumberBox()) }, modifier) {
        set(scope) { input.scope = it }
        set(onValueChange) { this.onValueChange = it }
        set(enabled) { element.IsEnabled = it }
        set(placeholder) { element.PlaceholderText = it }
        // The range must be in place before the value is synced, or the control clamps to the old range.
        set(minimum) { value -> input.programmatic { element.Minimum = value } }
        set(maximum) { value -> input.programmatic { element.Maximum = value } }
        set(step) { element.SmallChange = it; element.LargeChange = it * 10 }
        set(spinButtons) {
            element.SpinButtonPlacementMode =
                if (it) NumberBoxSpinButtonPlacementMode.Inline else NumberBoxSpinButtonPlacementMode.Hidden
        }
        reconcile { input.sync(value) }
    }
}

internal class NumberBoxNode(box: XamlNumberBox) : ElementNode<XamlNumberBox>(box, ChildSlot.None("NumberBox")) {
    var onValueChange: (Double) -> Unit = {}

    val input = ControlledValue(read = { box.Value }, write = { box.Value = it })

    init {
        onRelease(box.ValueChanged.subscribe { _, _ -> input.userChanged(onValueChange) })
    }
}
