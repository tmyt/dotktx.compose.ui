package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import System.Collections.ObjectModel.ObservableCollection
import Microsoft.UI.Xaml.Controls.BreadcrumbBar as XamlBreadcrumbBar

/**
 * A trail of locations, from the root to the current one (`BreadcrumbBar`). Clicking an item calls
 * [onItemClick] with its index; the last item is the current location. When the bar is too narrow,
 * the first items collapse into an ellipsis menu.
 *
 * ```
 * BreadcrumbBar(path, onItemClick = { path = path.take(it + 1) })
 * ```
 */
@Composable
fun BreadcrumbBar(
    items: List<String>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Leaf({ BreadcrumbBarNode(XamlBreadcrumbBar()) }, modifier) {
        set(onItemClick) { this.onItemClick = it }
        set(items) { setItems(it) }
    }
}

internal class BreadcrumbBarNode(bar: XamlBreadcrumbBar) : ElementNode<XamlBreadcrumbBar>(bar, ChildSlot.None("BreadcrumbBar")) {
    var onItemClick: (Int) -> Unit = {}
    private val source = ObservableCollection<Any?>()

    init {
        bar.ItemsSource = source
        onRelease(bar.ItemClicked.subscribe { _, e -> onItemClick(e.Index) })
    }

    fun setItems(items: List<String>) {
        source.Clear()
        for (item in items) source.Add(item)
    }
}
