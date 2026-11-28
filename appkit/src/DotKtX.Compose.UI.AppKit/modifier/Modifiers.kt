package dotktx.compose.ui.appkit

import System.Runtime.InteropServices.NFloat

/** Position inside the space the parent offers. Start/End are leading/trailing (left/right in left-to-right layouts). */
data class Alignment(val horizontal: Horizontal, val vertical: Vertical) {
    enum class Horizontal { Leading, Center, Trailing, Stretch }
    enum class Vertical { Top, Center, Bottom, Stretch }

    companion object {
        val TopStart = Alignment(Horizontal.Leading, Vertical.Top)
        val TopCenter = Alignment(Horizontal.Center, Vertical.Top)
        val TopEnd = Alignment(Horizontal.Trailing, Vertical.Top)
        val CenterStart = Alignment(Horizontal.Leading, Vertical.Center)
        val Center = Alignment(Horizontal.Center, Vertical.Center)
        val CenterEnd = Alignment(Horizontal.Trailing, Vertical.Center)
        val BottomStart = Alignment(Horizontal.Leading, Vertical.Bottom)
        val BottomCenter = Alignment(Horizontal.Center, Vertical.Bottom)
        val BottomEnd = Alignment(Horizontal.Trailing, Vertical.Bottom)

        /** Fill the offered space in both directions (the default inside window content, `Box` and `ScrollView`). */
        val Stretch = Alignment(Horizontal.Stretch, Vertical.Stretch)
    }
}

/** Space on the four sides of a view, in points. */
data class Insets(val start: Double, val top: Double, val end: Double, val bottom: Double) {
    val horizontal: Double get() = start + end
    val vertical: Double get() = top + bottom

    companion object {
        val Zero = Insets(0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * Layout properties of a node, set by the layout modifiers and read by the enclosing [Panel] when
 * it measures and arranges its children. `NaN` leaves a size unset; a `null` [alignment] uses the
 * parent's default ([Alignment.Stretch] in a [ContentHost], the `alignment` parameter of a `StackView`).
 * [padding] is honored by panels only.
 */
internal data class LayoutParams(
    val alignment: Alignment? = null,
    val margin: Insets = Insets.Zero,
    val padding: Insets = Insets.Zero,
    val width: Double = Double.NaN,
    val height: Double = Double.NaN,
    val minWidth: Double = Double.NaN,
    val maxWidth: Double = Double.NaN,
    val minHeight: Double = Double.NaN,
    val maxHeight: Double = Double.NaN,
)

/** Where the view sits inside the space its parent offers; `Stretch` fills it. */
fun Modifier.align(alignment: Alignment): Modifier = this then AlignModifier(alignment)

/** Space outside the view, in points. */
fun Modifier.margin(all: Double): Modifier = margin(all, all, all, all)

fun Modifier.margin(horizontal: Double, vertical: Double): Modifier = margin(horizontal, vertical, horizontal, vertical)

fun Modifier.margin(start: Double, top: Double, end: Double, bottom: Double): Modifier =
    this then MarginModifier(Insets(start, top, end, bottom))

/**
 * Space between a panel's edge and its children, in points. Only panels (`StackView`, `Box`, window
 * content) have children to pad; on any other view the modifier has no effect, and a warning is
 * printed once per view type.
 */
fun Modifier.padding(all: Double): Modifier = padding(all, all, all, all)

fun Modifier.padding(horizontal: Double, vertical: Double): Modifier = padding(horizontal, vertical, horizontal, vertical)

fun Modifier.padding(start: Double, top: Double, end: Double, bottom: Double): Modifier =
    this then PaddingModifier(Insets(start, top, end, bottom))

/** Fixed width, in points. */
fun Modifier.width(width: Double): Modifier = this then SizeModifier(SizeModifier.Width, width)

/** Fixed height, in points. */
fun Modifier.height(height: Double): Modifier = this then SizeModifier(SizeModifier.Height, height)

fun Modifier.size(width: Double, height: Double): Modifier = width(width).height(height)

/** Minimum and maximum width, in points. `NaN` leaves that bound unset. */
fun Modifier.widthIn(min: Double = Double.NaN, max: Double = Double.NaN): Modifier =
    bound(SizeModifier.MinWidth, min).bound(SizeModifier.MaxWidth, max)

/** Minimum and maximum height, in points. `NaN` leaves that bound unset. */
fun Modifier.heightIn(min: Double = Double.NaN, max: Double = Double.NaN): Modifier =
    bound(SizeModifier.MinHeight, min).bound(SizeModifier.MaxHeight, max)

private fun Modifier.bound(property: String, value: Double): Modifier =
    if (value.isNaN()) this else this then SizeModifier(property, value)

/** `NSView.AlphaValue`, from 0.0 to 1.0. */
fun Modifier.alpha(alpha: Double): Modifier = this then AlphaModifier(alpha)

/** Text shown when the pointer rests on the view (`NSView.ToolTip`). */
fun Modifier.toolTip(text: String): Modifier = this then ToolTipModifier(text)

/** Name that VoiceOver and accessibility clients report for the view (`accessibilityLabel`). */
fun Modifier.semantics(name: String): Modifier = this then SemanticsModifier(name)

/** Identifier for finding the view in UI tests (`accessibilityIdentifier`). */
fun Modifier.testTag(tag: String): Modifier = this then TestTagModifier(tag)

/** Hides the view (`NSView.Hidden`); a hidden child takes no space in a panel. */
fun Modifier.hidden(hidden: Boolean): Modifier = this then HiddenModifier(hidden)

private data class AlignModifier(val alignment: Alignment) : ElementModifier {
    override val key: Any get() = AlignModifier::class
    override fun apply(node: ElementNode<*>) { node.layout = node.layout.copy(alignment = alignment) }
    override fun reset(node: ElementNode<*>) { node.layout = node.layout.copy(alignment = null) }
}

private data class MarginModifier(val insets: Insets) : ElementModifier {
    override val key: Any get() = MarginModifier::class
    override fun apply(node: ElementNode<*>) { node.layout = node.layout.copy(margin = insets) }
    override fun reset(node: ElementNode<*>) { node.layout = node.layout.copy(margin = Insets.Zero) }
}

private data class PaddingModifier(val insets: Insets) : ElementModifier {
    override val key: Any get() = PaddingModifier::class

    override fun apply(node: ElementNode<*>) {
        if (node.view !is Panel) warnNoPadding(node)
        node.layout = node.layout.copy(padding = insets)
        (node.view as? Panel)?.padding = insets
    }

    override fun reset(node: ElementNode<*>) {
        node.layout = node.layout.copy(padding = Insets.Zero)
        (node.view as? Panel)?.padding = Insets.Zero
    }
}

private val warnedNoPadding = HashSet<String>()

private fun warnNoPadding(node: ElementNode<*>) {
    val type = node.view::class.simpleName ?: return
    if (warnedNoPadding.add(type)) println("dotktx.compose.ui.appkit: Modifier.padding has no effect on $type, which has no children to pad")
}

/** One of the six size bounds; the [property] name is the key, so each bound is diffed on its own. */
private data class SizeModifier(val property: String, val value: Double) : ElementModifier {
    override val key: Any get() = property

    override fun apply(node: ElementNode<*>) { node.layout = with(node.layout) { set(property, value) } }
    override fun reset(node: ElementNode<*>) { node.layout = with(node.layout) { set(property, Double.NaN) } }

    private fun LayoutParams.set(property: String, value: Double): LayoutParams = when (property) {
        Width -> copy(width = value)
        Height -> copy(height = value)
        MinWidth -> copy(minWidth = value)
        MaxWidth -> copy(maxWidth = value)
        MinHeight -> copy(minHeight = value)
        else -> copy(maxHeight = value)
    }

    companion object {
        const val Width = "width"
        const val Height = "height"
        const val MinWidth = "minWidth"
        const val MaxWidth = "maxWidth"
        const val MinHeight = "minHeight"
        const val MaxHeight = "maxHeight"
    }
}

private data class AlphaModifier(val alpha: Double) : ElementModifier {
    override val key: Any get() = AlphaModifier::class
    override fun apply(node: ElementNode<*>) { node.view.AlphaValue = NFloat(alpha) }
    override fun reset(node: ElementNode<*>) { node.view.AlphaValue = NFloat(1.0) }
}

private data class ToolTipModifier(val text: String) : ElementModifier {
    override val key: Any get() = ToolTipModifier::class
    override fun apply(node: ElementNode<*>) { node.view.ToolTip = text }
    override fun reset(node: ElementNode<*>) { node.view.ToolTip = null }
}

private data class SemanticsModifier(val name: String) : ElementModifier {
    override val key: Any get() = SemanticsModifier::class
    override fun apply(node: ElementNode<*>) { node.view.AccessibilityLabel = name }
    override fun reset(node: ElementNode<*>) { node.view.AccessibilityLabel = null }
}

private data class TestTagModifier(val tag: String) : ElementModifier {
    override val key: Any get() = TestTagModifier::class
    override fun apply(node: ElementNode<*>) { node.view.AccessibilityIdentifier = tag }
    override fun reset(node: ElementNode<*>) { node.view.AccessibilityIdentifier = null }
}

private data class HiddenModifier(val hidden: Boolean) : ElementModifier {
    override val key: Any get() = HiddenModifier::class
    override fun apply(node: ElementNode<*>) { node.view.Hidden = hidden; node.invalidateLayout() }
    override fun reset(node: ElementNode<*>) { node.view.Hidden = false; node.invalidateLayout() }
}
