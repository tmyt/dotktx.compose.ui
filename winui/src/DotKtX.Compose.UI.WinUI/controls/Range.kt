package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.ProgressBar as XamlProgressBar
import Microsoft.UI.Xaml.Controls.Slider as XamlSlider

/**
 * A slider (`Slider`), controlled by [value]. [step] is the value change per tick and keyboard step.
 *
 * ```
 * Slider(value = volume, onValueChange = { volume = it }, maximum = 10.0)
 * ```
 */
@Composable
fun Slider(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    minimum: Double = 0.0,
    maximum: Double = 100.0,
    step: Double = 1.0,
    enabled: Boolean = true,
) {
    val scope = currentRecomposeScope
    Leaf({ SliderNode(XamlSlider()) }, modifier) {
        set(scope) { input.scope = it }
        set(onValueChange) { this.onValueChange = it }
        set(enabled) { element.IsEnabled = it }
        // The range must be in place before the value is synced, or WinUI clamps the value to the old range.
        set(minimum) { value -> input.programmatic { element.Minimum = value } }
        set(maximum) { value -> input.programmatic { element.Maximum = value } }
        set(step) { element.StepFrequency = it; element.SmallChange = it }
        reconcile { input.sync(value) }
    }
}

internal class SliderNode(slider: XamlSlider) : ElementNode<XamlSlider>(slider, ChildSlot.None("Slider")) {
    var onValueChange: (Double) -> Unit = {}

    val input = ControlledValue(read = { slider.Value }, write = { slider.Value = it })

    init {
        onRelease(slider.ValueChanged.subscribe { _, _ -> input.userChanged(onValueChange) })
    }
}

/**
 * A progress bar (`ProgressBar`) showing [value] out of [maximum], or an animation when
 * [indeterminate] is true.
 */
@Composable
fun ProgressBar(
    value: Double,
    modifier: Modifier = Modifier,
    maximum: Double = 100.0,
    indeterminate: Boolean = false,
) {
    Leaf({ ElementNode(XamlProgressBar(), ChildSlot.None("ProgressBar")) }, modifier) {
        set(maximum) { element.Maximum = it }
        set(value) { element.Value = it }
        set(indeterminate) { element.IsIndeterminate = it }
    }
}
