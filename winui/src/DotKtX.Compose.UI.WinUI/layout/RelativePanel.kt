package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import Microsoft.UI.Xaml.DependencyProperty
import Microsoft.UI.Xaml.FrameworkElement
import Microsoft.UI.Xaml.Controls.RelativePanel as XamlRelativePanel

/**
 * Lays out its children relative to each other and to the panel (`RelativePanel`), the XAML
 * counterpart of Compose's `ConstraintLayout`.
 *
 * Children refer to each other through references created with [RelativePanelScope.createRefs]. A
 * child takes a reference and its constraints with [RelativePanelScope.relative]. Constraints name
 * their targets by reference, so the order in which children are emitted does not matter. Children
 * without constraints sit at the top-left corner.
 *
 * ```
 * RelativePanel {
 *     val (title, body, action) = createRefs()
 *     TextBlock("Title", Modifier.relative(title) { alignTopWithPanel(); alignLeftWithPanel() })
 *     TextBlock("Body", Modifier.relative(body) { below(title); alignLeftWith(title) })
 *     Button(onClick = {}, Modifier.relative(action) { rightOf(body); alignBottomWith(body) }) {
 *         TextBlock("OK")
 *     }
 * }
 * ```
 */
@Composable
fun RelativePanel(
    modifier: Modifier = Modifier,
    content: @Composable RelativePanelScope.() -> Unit,
) {
    Container({ relativePanelNode(XamlRelativePanel()) }, modifier, {}) { RelativePanelScope.content() }
}

private fun relativePanelNode(panel: XamlRelativePanel): ElementNode<XamlRelativePanel> =
    ElementNode(panel, ChildSlot.Many(panel.Children))

/** Receiver of [RelativePanel]'s content. */
@LayoutScopeMarker
object RelativePanelScope {
    /**
     * References for children of this panel, taken by destructuring: `val (a, b) = createRefs()`.
     * They are remembered, so they stay the same across recompositions.
     */
    @Composable
    fun createRefs(): RelativeRefs = remember { RelativeRefs() }

    /** A single reference; see [createRefs]. */
    @Composable
    fun createRef(): RelativeRef = remember { RelativeRef() }

    /**
     * Gives this child the reference [ref] and places it by [constraints]. Each child takes its own
     * reference; constraints that name a reference no child has are ignored by the panel.
     */
    fun Modifier.relative(ref: RelativeRef, constraints: RelativeConstraints.() -> Unit = {}): Modifier =
        this then RelativeModifier(ref, RelativeConstraints().apply(constraints).build())
}

/**
 * Identifies a child of a [RelativePanel] in other children's constraints. Each reference carries a
 * name that is unique in the process; the child's `FrameworkElement.Name` is set to it, and the
 * panel resolves constraints by name among its children.
 */
@Stable
class RelativeRef internal constructor() {
    internal val name: String = "dotktx.relative." + nextId++

    override fun toString(): String = name

    private companion object {
        var nextId = 0
    }
}

/** References for destructuring; each component is created on first use and then kept. */
@Stable
class RelativeRefs internal constructor() {
    private val refs = ArrayList<RelativeRef>()

    private fun ref(index: Int): RelativeRef {
        while (refs.size <= index) refs.add(RelativeRef())
        return refs[index]
    }

    operator fun component1(): RelativeRef = ref(0)
    operator fun component2(): RelativeRef = ref(1)
    operator fun component3(): RelativeRef = ref(2)
    operator fun component4(): RelativeRef = ref(3)
    operator fun component5(): RelativeRef = ref(4)
    operator fun component6(): RelativeRef = ref(5)
    operator fun component7(): RelativeRef = ref(6)
    operator fun component8(): RelativeRef = ref(7)
    operator fun component9(): RelativeRef = ref(8)
    operator fun component10(): RelativeRef = ref(9)
}

/**
 * The constraints of one child of a [RelativePanel]. Constraints against other children take their
 * references; constraints against the panel take no argument.
 */
class RelativeConstraints internal constructor() {
    private val targets = LinkedHashMap<RelativeEdge, RelativeRef>()
    private val panelEdges = LinkedHashSet<RelativePanelEdge>()

    /** Places this child above [ref]. */
    fun above(ref: RelativeRef) { targets[RelativeEdge.Above] = ref }
    /** Places this child below [ref]. */
    fun below(ref: RelativeRef) { targets[RelativeEdge.Below] = ref }
    /** Places this child to the left of [ref]. */
    fun leftOf(ref: RelativeRef) { targets[RelativeEdge.LeftOf] = ref }
    /** Places this child to the right of [ref]. */
    fun rightOf(ref: RelativeRef) { targets[RelativeEdge.RightOf] = ref }
    /** Aligns this child's left edge with [ref]'s left edge. */
    fun alignLeftWith(ref: RelativeRef) { targets[RelativeEdge.AlignLeftWith] = ref }
    /** Aligns this child's right edge with [ref]'s right edge. */
    fun alignRightWith(ref: RelativeRef) { targets[RelativeEdge.AlignRightWith] = ref }
    /** Aligns this child's top edge with [ref]'s top edge. */
    fun alignTopWith(ref: RelativeRef) { targets[RelativeEdge.AlignTopWith] = ref }
    /** Aligns this child's bottom edge with [ref]'s bottom edge. */
    fun alignBottomWith(ref: RelativeRef) { targets[RelativeEdge.AlignBottomWith] = ref }
    /** Centers this child horizontally on [ref]. */
    fun alignHorizontalCenterWith(ref: RelativeRef) { targets[RelativeEdge.AlignHorizontalCenterWith] = ref }
    /** Centers this child vertically on [ref]. */
    fun alignVerticalCenterWith(ref: RelativeRef) { targets[RelativeEdge.AlignVerticalCenterWith] = ref }

    /** Aligns this child's left edge with the panel's left edge. */
    fun alignLeftWithPanel() { panelEdges.add(RelativePanelEdge.Left) }
    /** Aligns this child's right edge with the panel's right edge. */
    fun alignRightWithPanel() { panelEdges.add(RelativePanelEdge.Right) }
    /** Aligns this child's top edge with the panel's top edge. */
    fun alignTopWithPanel() { panelEdges.add(RelativePanelEdge.Top) }
    /** Aligns this child's bottom edge with the panel's bottom edge. */
    fun alignBottomWithPanel() { panelEdges.add(RelativePanelEdge.Bottom) }
    /** Centers this child horizontally in the panel. */
    fun alignHorizontalCenterWithPanel() { panelEdges.add(RelativePanelEdge.HorizontalCenter) }
    /** Centers this child vertically in the panel. */
    fun alignVerticalCenterWithPanel() { panelEdges.add(RelativePanelEdge.VerticalCenter) }

    internal fun build(): RelativeLayout = RelativeLayout(targets.mapValues { it.value.name }, panelEdges.toSet())
}

internal enum class RelativeEdge(val property: DependencyProperty) {
    Above(XamlRelativePanel.AboveProperty),
    Below(XamlRelativePanel.BelowProperty),
    LeftOf(XamlRelativePanel.LeftOfProperty),
    RightOf(XamlRelativePanel.RightOfProperty),
    AlignLeftWith(XamlRelativePanel.AlignLeftWithProperty),
    AlignRightWith(XamlRelativePanel.AlignRightWithProperty),
    AlignTopWith(XamlRelativePanel.AlignTopWithProperty),
    AlignBottomWith(XamlRelativePanel.AlignBottomWithProperty),
    AlignHorizontalCenterWith(XamlRelativePanel.AlignHorizontalCenterWithProperty),
    AlignVerticalCenterWith(XamlRelativePanel.AlignVerticalCenterWithProperty),
}

internal enum class RelativePanelEdge(val property: DependencyProperty) {
    Left(XamlRelativePanel.AlignLeftWithPanelProperty),
    Right(XamlRelativePanel.AlignRightWithPanelProperty),
    Top(XamlRelativePanel.AlignTopWithPanelProperty),
    Bottom(XamlRelativePanel.AlignBottomWithPanelProperty),
    HorizontalCenter(XamlRelativePanel.AlignHorizontalCenterWithPanelProperty),
    VerticalCenter(XamlRelativePanel.AlignVerticalCenterWithPanelProperty),
}

/** The resolved constraints of one child: target names by edge, and the panel edges it aligns with. */
internal data class RelativeLayout(val targets: Map<RelativeEdge, String>, val panelEdges: Set<RelativePanelEdge>)

/**
 * Names the element after its reference and writes the constraints as names, which the panel
 * resolves among its children during layout. Every attached property is written on each apply, so
 * a constraint that disappears from the chain is cleared.
 */
private data class RelativeModifier(val ref: RelativeRef, val layout: RelativeLayout) : ElementModifier {
    override val key: Any get() = RelativeModifier::class

    override fun apply(element: FrameworkElement) {
        element.Name = ref.name
        for (edge in RelativeEdge.entries) {
            val target = layout.targets[edge]
            if (target == null) element.ClearValue(edge.property) else element.SetValue(edge.property, target)
        }
        for (edge in RelativePanelEdge.entries) {
            if (edge in layout.panelEdges) element.SetValue(edge.property, true) else element.ClearValue(edge.property)
        }
    }

    override fun reset(element: FrameworkElement) {
        element.ClearValue(FrameworkElement.NameProperty)
        for (edge in RelativeEdge.entries) element.ClearValue(edge.property)
        for (edge in RelativePanelEdge.entries) element.ClearValue(edge.property)
    }
}
