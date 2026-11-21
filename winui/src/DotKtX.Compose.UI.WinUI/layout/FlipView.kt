package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import Microsoft.UI.Xaml.Controls.FlipView as XamlFlipView

/**
 * Shows one item at a time with buttons to flip to the previous and next one (`FlipView`), controlled
 * by [selectedIndex]. Flipping calls [onSelectedIndexChange].
 *
 * Only the selected item and its neighbours get elements. Each realized item is composed by
 * [itemContent] in a composition of its own; keys behave as in [ListView]. Give the view a bounded
 * size.
 *
 * ```
 * FlipView(photos, selectedIndex = page, onSelectedIndexChange = { page = it }, Modifier.height(300.0)) { photo ->
 *     Image(photo.uri)
 * }
 * ```
 */
@Composable
fun <T> FlipView(
    items: List<T>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val parent = rememberCompositionContext()
    val containers = remember { ContainerItems<T>(parent, "FlipView") }
    val scope = currentRecomposeScope
    Leaf({ FlipViewNode(XamlFlipView(), containers) }, modifier) {
        set(scope) { selection.scope = it }
        set(onSelectedIndexChange) { this.onSelectedIndexChange = it }
        reconcile { update(items, key, itemContent, selectedIndex) }
    }
    DisposableEffect(containers) {
        onDispose { containers.dispose() }
    }
}

/**
 * `FlipView` is not a `ListViewBase` and raises no `ContainerContentChanging`. Its containers use the
 * same empty-`Grid` item template as [ListView], and after every layout pass the containers its
 * panel shows are scanned: a host whose container now shows another item is bound again. Only the
 * selected item and its neighbours are realized, so the scan is short, and it binds nothing while
 * the containers keep their items. Overriding `PrepareContainerForItemOverride` instead would need a
 * Kotlin subclass of `FlipView`, which XAML does not know and refuses the `FlipView` style for.
 */
internal class FlipViewNode<T>(private val view: XamlFlipView, private val containers: ContainerItems<T>) :
    ElementNode<XamlFlipView>(view, ChildSlot.None("FlipView")) {
    var onSelectedIndexChange: (Int) -> Unit = {}
    private val source = KeyedSource { containers.compositions.textOf(it) }

    val selection = ControlledValue(
        read = { view.SelectedIndex },
        write = { view.SelectedIndex = if (it >= 0 && it < source.Count) it else -1 },
    )

    init {
        view.ItemTemplate = itemHostTemplate()
        view.ItemsSource = source
        onRelease(view.SelectionChanged.subscribe { _, _ -> selection.userChanged(onSelectedIndexChange) })
        onRelease(view.LayoutUpdated.subscribe { _, _ -> scan() })
    }

    private fun scan() {
        containers.scan(view.ItemsPanelRoot) { idOf(view.ItemFromContainer(it)) }
    }

    /** Changing the items can move WinUI's selection; that is not a user change, and sync restores it. */
    fun update(items: List<T>, key: ((T) -> Any)?, content: @Composable (T) -> Unit, selectedIndex: Int) {
        selection.programmatic {
            if (containers.compositions.update(items, key, content)) source.edit(containers.compositions.ids)
        }
        containers.compositions.refresh()
        selection.sync(selectedIndex)
    }
}
