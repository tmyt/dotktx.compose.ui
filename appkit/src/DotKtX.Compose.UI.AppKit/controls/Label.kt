package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import AppKit.NSFont
import AppKit.NSLineBreakMode
import AppKit.NSTextField
import System.IntPtr
import System.Runtime.InteropServices.NFloat

/**
 * A read-only text label (`NSTextField` configured as a label). [fontSize] in points; NaN keeps the
 * system font size. With [wrap] the text breaks into several lines instead of being truncated.
 *
 * ```
 * Label("Hello", fontSize = 18.0)
 * ```
 */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Double = Double.NaN,
    wrap: Boolean = false,
) {
    Leaf({ LabelNode(createLabel()) }, modifier) {
        set(text) { field.StringValue = it; invalidateLayout() }
        set(wrap) {
            field.Cell.Wraps = it
            field.Cell.UsesSingleLineMode = !it
            field.LineBreakMode = if (it) NSLineBreakMode.ByWordWrapping else NSLineBreakMode.TruncatingTail
            field.MaximumNumberOfLines = IntPtr(if (it) 0 else 1)
            invalidateLayout()
        }
        set(fontSize) {
            field.Font = if (it.isNaN()) NSFont.SystemFontOfSize(NSFont.SystemFontSize)!! else NSFont.SystemFontOfSize(NFloat(it))!!
            invalidateLayout()
        }
    }
}

private fun createLabel(): NSTextField {
    val field = NSTextField()
    field.Editable = false
    field.Bezeled = false
    field.DrawsBackground = false
    field.Selectable = false
    return field
}

internal class LabelNode(val field: NSTextField) : ElementNode<NSTextField>(field, ChildSlot.None("Label"))
