package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import AppKit.NSProgressIndicator
import AppKit.NSProgressIndicatorStyle

/**
 * A determinate progress bar (`NSProgressIndicator`) showing [value] between [min] and [max]. Display only.
 *
 * ```
 * ProgressIndicator(value = done, max = total)
 * ```
 */
@Composable
fun ProgressIndicator(
    value: Double,
    modifier: Modifier = Modifier,
    min: Double = 0.0,
    max: Double = 100.0,
) {
    Leaf({ ProgressIndicatorNode(newBar()) }, modifier) {
        set(min) { bar.MinValue = it }
        set(max) { bar.MaxValue = it }
        set(value) { bar.DoubleValue = it }
    }
}

private fun newBar(): NSProgressIndicator {
    val bar = NSProgressIndicator()
    bar.Style = NSProgressIndicatorStyle.Bar
    bar.Indeterminate = false
    return bar
}

internal class ProgressIndicatorNode(val bar: NSProgressIndicator) :
    ElementNode<NSProgressIndicator>(bar, ChildSlot.None("ProgressIndicator"))
