package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.Border
import Microsoft.UI.Xaml.Controls.SplitViewDisplayMode
import Microsoft.UI.Xaml.Controls.SplitView as XamlSplitView

/**
 * A pane beside a content area (`SplitView`). The pane is controlled by [paneOpen]; when the user
 * closes it (light dismiss in the overlay modes), [onPaneOpenChange] is called with `false`.
 *
 * [displayMode] decides whether the open pane overlays or pushes the content and whether a compact
 * strip stays visible while it is closed. [openPaneLength] and [compactPaneLength] are widths in
 * effective pixels; `NaN` keeps WinUI's widths. [pane] and [content] must each emit at most one
 * element.
 *
 * ```
 * SplitView(paneOpen = menuOpen, onPaneOpenChange = { menuOpen = it }, pane = { Menu() }) {
 *     Page()
 * }
 * ```
 */
@Composable
fun SplitView(
    paneOpen: Boolean,
    onPaneOpenChange: (Boolean) -> Unit,
    pane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    displayMode: SplitViewDisplayMode = SplitViewDisplayMode.Overlay,
    openPaneLength: Double = Double.NaN,
    compactPaneLength: Double = Double.NaN,
    content: @Composable () -> Unit,
) {
    val scope = currentRecomposeScope
    Container({ SplitViewNode(XamlSplitView()) }, modifier, {
        set(scope) { input.scope = it }
        set(onPaneOpenChange) { this.onPaneOpenChange = it }
        // The display mode decides whether the pane can be open at all; set it before the pane is synced.
        set(displayMode) { mode -> input.programmatic { element.DisplayMode = mode } }
        set(openPaneLength) { if (it.isNaN()) element.ClearValue(XamlSplitView.OpenPaneLengthProperty) else element.OpenPaneLength = it }
        set(compactPaneLength) {
            if (it.isNaN()) element.ClearValue(XamlSplitView.CompactPaneLengthProperty) else element.CompactPaneLength = it
        }
        reconcile { input.sync(paneOpen) }
    }) {
        SplitViewHost(isPane = true, pane)
        SplitViewHost(isPane = false, content)
    }
}

/**
 * Holder of the pane or the content. Both are always emitted, so each slot keeps its place no matter
 * what the application's lambdas emit, and the split view tells them apart by [isPane] rather than by
 * position.
 */
@Composable
private fun SplitViewHost(isPane: Boolean, content: @Composable () -> Unit) {
    Container({ SplitViewHostNode(Border(), isPane) }, Modifier, {}, content)
}

internal class SplitViewHostNode(border: Border, val isPane: Boolean) :
    ElementNode<Border>(border, ChildSlot.Single(if (isPane) "SplitView pane" else "SplitView") { border.Child = it })

/** Puts the pane host into `SplitView.Pane` and the content host into `SplitView.Content`. */
internal class SplitViewSlot(private val view: XamlSplitView) : ChildSlot {
    private val hosts = ArrayList<ElementNode<*>>()

    override fun insert(index: Int, child: ElementNode<*>) {
        hosts.add(index, child)
        place()
    }

    override fun remove(index: Int, count: Int) {
        for (i in 0 until count) hosts.removeAt(index)
        place()
    }

    override fun move(from: Int, to: Int, count: Int) {
        throw IllegalStateException("SplitView pane and content cannot be moved")
    }

    override fun clear() {
        hosts.clear()
        place()
    }

    private fun place() {
        val pane = hosts.firstOrNull { (it as? SplitViewHostNode)?.isPane == true }?.element
        val content = hosts.firstOrNull { (it as? SplitViewHostNode)?.isPane == false }?.element
        if (view.Pane !== pane) view.Pane = pane
        if (view.Content !== content) view.Content = content
    }
}

internal class SplitViewNode(view: XamlSplitView) : ElementNode<XamlSplitView>(view, SplitViewSlot(view)) {
    var onPaneOpenChange: (Boolean) -> Unit = {}

    val input = ControlledValue(read = { view.IsPaneOpen }, write = { view.IsPaneOpen = it })

    init {
        // PaneClosed is raised after the closing animation, and a recomposition before it would reopen
        // the pane. The property callback runs as IsPaneOpen changes.
        val token = view.RegisterPropertyChangedCallback(XamlSplitView.IsPaneOpenProperty) { _, _ ->
            input.userChanged(onPaneOpenChange)
        }
        onRelease(object : AutoCloseable {
            override fun close() {
                view.UnregisterPropertyChangedCallback(XamlSplitView.IsPaneOpenProperty, token)
            }
        })
    }
}
