package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import dotktx.compose.ui.annotations.EmitsOneElement
import AppKit.NSSlider

/**
 * A horizontal slider (`NSSlider`), controlled by [value] between [min] and [max].
 *
 * ```
 * Slider(value = volume, onValueChange = { volume = it }, min = 0.0, max = 10.0)
 * ```
 */
@EmitsOneElement
@Composable
fun Slider(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    min: Double = 0.0,
    max: Double = 100.0,
) {
    val scope = currentRecomposeScope
    Leaf({ SliderNode(NSSlider().also { it.Continuous = true }) }, modifier) {
        set(scope) { input.scope = it }
        set(onValueChange) { this.onValueChange = it }
        set(enabled) { slider.Enabled = it }
        set(min) { slider.MinValue = it }
        set(max) { slider.MaxValue = it }
        reconcile { input.sync(value) }
    }
}

internal class SliderNode(val slider: NSSlider) : ElementNode<NSSlider>(slider, ChildSlot.None("Slider")) {
    var onValueChange: (Double) -> Unit = {}

    val input = ControlledValue(read = { slider.DoubleValue }, write = { slider.DoubleValue = it })

    init {
        onRelease(slider.Activated.subscribe { _, _ -> input.userChanged(onValueChange) })
    }
}
