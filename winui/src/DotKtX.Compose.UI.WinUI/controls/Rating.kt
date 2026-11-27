package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.Controls.RatingControl as XamlRatingControl

/**
 * A row of stars for rating something (`RatingControl`), controlled by [value]: the number of stars
 * from 1 to [maxRating], or `NaN` when nothing is rated. Choosing a star calls [onValueChange];
 * choosing the current rating again clears it and reports `NaN`. With [isReadOnly] the stars only
 * display [value].
 *
 * ```
 * RatingControl(value = stars, onValueChange = { stars = it })
 * ```
 */
@EmitsOneElement
@Composable
fun RatingControl(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    maxRating: Int = 5,
    isReadOnly: Boolean = false,
    enabled: Boolean = true,
) {
    val scope = currentRecomposeScope
    Leaf({ RatingControlNode(XamlRatingControl()) }, modifier) {
        set(scope) { input.scope = it }
        set(onValueChange) { this.onValueChange = it }
        set(enabled) { element.IsEnabled = it }
        set(isReadOnly) { element.IsReadOnly = it }
        // The maximum must be in place before the value is synced, or the control clamps to the old one.
        set(maxRating) { max -> input.programmatic { element.MaxRating = max } }
        reconcile { input.sync(if (value.isNaN()) RATING_UNSET else value) }
    }
}

/**
 * The control marks "not rated" with -1. The node keeps that raw value, so comparisons never involve
 * `NaN`, and translates at the composable's boundary.
 *
 * Changes are observed through the `Value` property's change callback rather than `ValueChanged`:
 * `ValueChanged` is not raised when assistive technology sets the value through the automation peer,
 * and such a change would otherwise neither reach the application nor be undone.
 */
internal class RatingControlNode(rating: XamlRatingControl) :
    ElementNode<XamlRatingControl>(rating, ChildSlot.None("RatingControl")) {
    var onValueChange: (Double) -> Unit = {}

    val input = ControlledValue(read = { rating.Value }, write = { rating.Value = it })

    init {
        val token = rating.RegisterPropertyChangedCallback(XamlRatingControl.ValueProperty) { _, _ ->
            input.userChanged { raw -> onValueChange(if (raw < 0.0) Double.NaN else raw) }
        }
        onRelease(AutoCloseable { rating.UnregisterPropertyChangedCallback(XamlRatingControl.ValueProperty, token) })
    }
}

private const val RATING_UNSET = -1.0
