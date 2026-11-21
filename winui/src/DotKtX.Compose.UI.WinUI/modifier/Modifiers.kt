package dotktx.compose.ui.winui

import Microsoft.UI.Xaml.DependencyProperty
import Microsoft.UI.Xaml.ElementTheme
import Microsoft.UI.Xaml.FrameworkElement
import Microsoft.UI.Xaml.HorizontalAlignment
import Microsoft.UI.Xaml.Thickness
import Microsoft.UI.Xaml.UIElement
import Microsoft.UI.Xaml.VerticalAlignment
import Microsoft.UI.Xaml.Automation.AutomationProperties
import Microsoft.UI.Xaml.Controls.Canvas
import Microsoft.UI.Xaml.Controls.Grid
import Microsoft.UI.Xaml.Controls.ToolTipService

/** Position inside the space the parent offers. Start/End follow the element's `FlowDirection`. */
data class Alignment(val horizontal: HorizontalAlignment, val vertical: VerticalAlignment) {
    companion object {
        val TopStart = Alignment(HorizontalAlignment.Left, VerticalAlignment.Top)
        val TopCenter = Alignment(HorizontalAlignment.Center, VerticalAlignment.Top)
        val TopEnd = Alignment(HorizontalAlignment.Right, VerticalAlignment.Top)
        val CenterStart = Alignment(HorizontalAlignment.Left, VerticalAlignment.Center)
        val Center = Alignment(HorizontalAlignment.Center, VerticalAlignment.Center)
        val CenterEnd = Alignment(HorizontalAlignment.Right, VerticalAlignment.Center)
        val BottomStart = Alignment(HorizontalAlignment.Left, VerticalAlignment.Bottom)
        val BottomCenter = Alignment(HorizontalAlignment.Center, VerticalAlignment.Bottom)
        val BottomEnd = Alignment(HorizontalAlignment.Right, VerticalAlignment.Bottom)

        /** Fill the offered space in both directions (WinUI's default). */
        val Stretch = Alignment(HorizontalAlignment.Stretch, VerticalAlignment.Stretch)
    }
}

/** `HorizontalAlignment` and `VerticalAlignment`. */
fun Modifier.align(alignment: Alignment): Modifier = this then AlignModifier(alignment)

/** `Margin`, in effective pixels. */
fun Modifier.margin(all: Double): Modifier = margin(all, all, all, all)

fun Modifier.margin(horizontal: Double, vertical: Double): Modifier = margin(horizontal, vertical, horizontal, vertical)

fun Modifier.margin(start: Double, top: Double, end: Double, bottom: Double): Modifier =
    this then MarginModifier(start, top, end, bottom)

/** `Width`, in effective pixels. */
fun Modifier.width(width: Double): Modifier =
    this then PropertyModifier(FrameworkElement.WidthProperty, width)

/** `Height`, in effective pixels. */
fun Modifier.height(height: Double): Modifier =
    this then PropertyModifier(FrameworkElement.HeightProperty, height)

fun Modifier.size(width: Double, height: Double): Modifier = width(width).height(height)

/** `MinWidth` and `MaxWidth`, in effective pixels. `NaN` leaves that bound unset. */
fun Modifier.widthIn(min: Double = Double.NaN, max: Double = Double.NaN): Modifier =
    bound(FrameworkElement.MinWidthProperty, min).bound(FrameworkElement.MaxWidthProperty, max)

/** `MinHeight` and `MaxHeight`, in effective pixels. `NaN` leaves that bound unset. */
fun Modifier.heightIn(min: Double = Double.NaN, max: Double = Double.NaN): Modifier =
    bound(FrameworkElement.MinHeightProperty, min).bound(FrameworkElement.MaxHeightProperty, max)

private fun Modifier.bound(property: DependencyProperty, value: Double): Modifier =
    if (value.isNaN()) this else this then PropertyModifier(property, value)

/** Text shown when the pointer rests on the element (`ToolTipService.ToolTip`). */
fun Modifier.toolTip(text: String): Modifier =
    this then PropertyModifier(ToolTipService.ToolTipProperty, text)

/** Name that screen readers and UI Automation report for the element (`AutomationProperties.Name`). */
fun Modifier.semantics(name: String): Modifier =
    this then PropertyModifier(AutomationProperties.NameProperty, name)

/** Identifier for finding the element in UI tests (`AutomationProperties.AutomationId`). */
fun Modifier.testTag(tag: String): Modifier =
    this then PropertyModifier(AutomationProperties.AutomationIdProperty, tag)

/** Light or dark theme for the element and its descendants (`RequestedTheme`). */
fun Modifier.requestedTheme(theme: ElementTheme): Modifier =
    this then PropertyModifier(FrameworkElement.RequestedThemeProperty, theme)

/** `Opacity`, from 0.0 to 1.0. */
fun Modifier.alpha(alpha: Double): Modifier =
    this then PropertyModifier(UIElement.OpacityProperty, alpha)

/** Cell of the enclosing `Grid` (`Grid.Row`, `Grid.Column`, `Grid.RowSpan`, `Grid.ColumnSpan`). */
fun Modifier.gridCell(row: Int, column: Int, rowSpan: Int = 1, columnSpan: Int = 1): Modifier =
    this then GridCellModifier(row, column, rowSpan, columnSpan)

/**
 * Position inside the enclosing `Canvas`, in effective pixels from its top-left corner (`Canvas.Left`,
 * `Canvas.Top`). [zIndex] orders overlapping children (`Canvas.ZIndex`, higher on top); `null` keeps
 * the order of the children.
 */
fun Modifier.canvasPosition(left: Double, top: Double, zIndex: Int? = null): Modifier =
    this then CanvasPositionModifier(left, top, zIndex)

/**
 * Sets any dependency property. Removing the modifier clears the local value, so the element falls
 * back to its style or template.
 *
 * ```
 * Modifier.property(Control.FontSizeProperty, 24.0)
 * ```
 */
fun Modifier.property(property: DependencyProperty, value: Any?): Modifier =
    this then PropertyModifier(property, value)

private data class AlignModifier(val alignment: Alignment) : ElementModifier {
    override val key: Any get() = AlignModifier::class

    override fun apply(element: FrameworkElement) {
        element.HorizontalAlignment = alignment.horizontal
        element.VerticalAlignment = alignment.vertical
    }

    override fun reset(element: FrameworkElement) {
        element.ClearValue(FrameworkElement.HorizontalAlignmentProperty)
        element.ClearValue(FrameworkElement.VerticalAlignmentProperty)
    }
}

private data class MarginModifier(val start: Double, val top: Double, val end: Double, val bottom: Double) : ElementModifier {
    override val key: Any get() = MarginModifier::class

    override fun apply(element: FrameworkElement) {
        element.Margin = Thickness(start, top, end, bottom)
    }

    override fun reset(element: FrameworkElement) {
        element.ClearValue(FrameworkElement.MarginProperty)
    }
}

private data class GridCellModifier(val row: Int, val column: Int, val rowSpan: Int, val columnSpan: Int) : ElementModifier {
    override val key: Any get() = GridCellModifier::class

    override fun apply(element: FrameworkElement) {
        Grid.SetRow(element, row)
        Grid.SetColumn(element, column)
        Grid.SetRowSpan(element, rowSpan)
        Grid.SetColumnSpan(element, columnSpan)
    }

    override fun reset(element: FrameworkElement) {
        element.ClearValue(Grid.RowProperty)
        element.ClearValue(Grid.ColumnProperty)
        element.ClearValue(Grid.RowSpanProperty)
        element.ClearValue(Grid.ColumnSpanProperty)
    }
}

private data class CanvasPositionModifier(val left: Double, val top: Double, val zIndex: Int?) : ElementModifier {
    override val key: Any get() = CanvasPositionModifier::class

    override fun apply(element: FrameworkElement) {
        Canvas.SetLeft(element, left)
        Canvas.SetTop(element, top)
        if (zIndex == null) element.ClearValue(Canvas.ZIndexProperty) else Canvas.SetZIndex(element, zIndex)
    }

    override fun reset(element: FrameworkElement) {
        element.ClearValue(Canvas.LeftProperty)
        element.ClearValue(Canvas.TopProperty)
        element.ClearValue(Canvas.ZIndexProperty)
    }
}

private data class PropertyModifier(val property: DependencyProperty, val value: Any?) : ElementModifier {
    override val key: Any get() = property

    override fun apply(element: FrameworkElement) {
        element.SetValue(property, value)
    }

    override fun reset(element: FrameworkElement) {
        element.ClearValue(property)
    }
}
