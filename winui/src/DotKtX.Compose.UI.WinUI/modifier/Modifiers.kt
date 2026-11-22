package dotktx.compose.ui.winui

import Microsoft.UI.Xaml.DependencyProperty
import Microsoft.UI.Xaml.ElementTheme
import Microsoft.UI.Xaml.FrameworkElement
import Microsoft.UI.Xaml.HorizontalAlignment
import Microsoft.UI.Xaml.Thickness
import Microsoft.UI.Xaml.UIElement
import Microsoft.UI.Xaml.VerticalAlignment
import Microsoft.UI.Xaml.Automation.AutomationProperties
import Microsoft.UI.Xaml.Controls.Border
import Microsoft.UI.Xaml.Controls.ContentPresenter
import Microsoft.UI.Xaml.Controls.Control
import Microsoft.UI.Xaml.Controls.Grid
import Microsoft.UI.Xaml.Controls.ItemsPresenter
import Microsoft.UI.Xaml.Controls.RelativePanel
import Microsoft.UI.Xaml.Controls.RichTextBlock
import Microsoft.UI.Xaml.Controls.StackPanel
import Microsoft.UI.Xaml.Controls.TextBlock
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

/**
 * `Padding`: space between the element's edge and its content, in effective pixels. Start/End follow
 * the element's `FlowDirection`. Removing the modifier restores the padding from the element's style.
 *
 * Every control has padding, as do `Border`, `StackPanel`, `Grid`, `RelativePanel`, `TextBlock` and
 * `RichTextBlock`. Elements without content to pad (`Image`, `Canvas`, `Viewbox`, shapes) have no
 * padding property; on them the modifier has no effect, and a warning is printed once per element
 * type.
 *
 * ```
 * Button(onClick = {}, modifier = Modifier.margin(8.0).padding(16.0, 8.0)) { TextBlock("OK") }
 * ```
 */
fun Modifier.padding(all: Double): Modifier = padding(all, all, all, all)

fun Modifier.padding(horizontal: Double, vertical: Double): Modifier = padding(horizontal, vertical, horizontal, vertical)

fun Modifier.padding(start: Double, top: Double, end: Double, bottom: Double): Modifier =
    this then PaddingModifier(start, top, end, bottom)

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

/**
 * WinUI defines `Padding` separately on each type that has it rather than on `FrameworkElement`, so
 * the property is looked up from the element's type.
 */
private data class PaddingModifier(val start: Double, val top: Double, val end: Double, val bottom: Double) : ElementModifier {
    override val key: Any get() = PaddingModifier::class

    override fun apply(element: FrameworkElement) {
        val property = paddingProperty(element)
        if (property == null) warnNoPadding(element) else element.SetValue(property, Thickness(start, top, end, bottom))
    }

    override fun reset(element: FrameworkElement) {
        paddingProperty(element)?.let { element.ClearValue(it) }
    }
}

private fun paddingProperty(element: FrameworkElement): DependencyProperty? = when (element) {
    is Control -> Control.PaddingProperty
    is Border -> Border.PaddingProperty
    is StackPanel -> StackPanel.PaddingProperty
    is Grid -> Grid.PaddingProperty
    is RelativePanel -> RelativePanel.PaddingProperty
    is TextBlock -> TextBlock.PaddingProperty
    is RichTextBlock -> RichTextBlock.PaddingProperty
    is ContentPresenter -> ContentPresenter.PaddingProperty
    is ItemsPresenter -> ItemsPresenter.PaddingProperty
    else -> null
}

private val warnedNoPadding = HashSet<String>()

private fun warnNoPadding(element: FrameworkElement) {
    val type = element::class.simpleName ?: return
    if (warnedNoPadding.add(type)) println("dotktx.compose.ui.winui: Modifier.padding has no effect on $type, which has no Padding property")
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
