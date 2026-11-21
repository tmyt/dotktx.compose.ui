package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Windows.UI.Color
import Microsoft.UI.Xaml.Controls.ColorPicker as XamlColorPicker

/**
 * A color spectrum with sliders and text fields for choosing a color (`ColorPicker`), controlled by
 * [color]. Every change the user makes calls [onColorChange].
 *
 * The color is WinUI's own `Windows.UI.Color`, so `Microsoft.UI.Colors` constants and
 * `SolidColorBrush` take it directly. [alphaEnabled] adds an opacity slider and text field;
 * otherwise the picker keeps the alpha channel of [color]. [moreButton] hides the RGB/HSV text fields
 * behind a "More" button.
 *
 * ```
 * var accent by remember { mutableStateOf(Colors.SteelBlue) }
 * ColorPicker(color = accent, onColorChange = { accent = it })
 * ```
 */
@Composable
fun ColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alphaEnabled: Boolean = false,
    moreButton: Boolean = false,
) {
    val scope = currentRecomposeScope
    Leaf({ ColorPickerNode(XamlColorPicker()) }, modifier) {
        set(scope) { input.scope = it }
        set(onColorChange) { this.onColorChange = it }
        set(enabled) { element.IsEnabled = it }
        set(alphaEnabled) { element.IsAlphaEnabled = it }
        set(moreButton) { element.IsMoreButtonVisible = it }
        reconcile { input.sync(color) }
    }
}

internal class ColorPickerNode(picker: XamlColorPicker) :
    ElementNode<XamlColorPicker>(picker, ChildSlot.None("ColorPicker")) {
    var onColorChange: (Color) -> Unit = {}

    val input = ControlledValue(read = { picker.Color }, write = { picker.Color = it })

    init {
        // ColorChanged is raised from the Color property's change callback, synchronously with the change.
        onRelease(picker.ColorChanged.subscribe { _, _ -> input.userChanged(onColorChange) })
    }
}
