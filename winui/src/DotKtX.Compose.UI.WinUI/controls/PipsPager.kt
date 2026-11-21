package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.PipsPagerButtonVisibility
import Microsoft.UI.Xaml.Controls.PipsPager as XamlPipsPager

/**
 * A row of dots for paging through [numberOfPages] pages (`PipsPager`), controlled by
 * [selectedPageIndex]. At most [maxVisiblePips] dots are shown at once. With [navigationButtons] the
 * pager also shows previous and next buttons.
 *
 * ```
 * PipsPager(numberOfPages = photos.size, selectedPageIndex = page, onSelectedPageIndexChange = { page = it })
 * ```
 */
@Composable
fun PipsPager(
    numberOfPages: Int,
    selectedPageIndex: Int,
    onSelectedPageIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxVisiblePips: Int = 5,
    navigationButtons: Boolean = false,
    enabled: Boolean = true,
) {
    val scope = currentRecomposeScope
    Leaf({ PipsPagerNode(XamlPipsPager()) }, modifier) {
        set(scope) { input.scope = it }
        set(onSelectedPageIndexChange) { this.onSelectedPageIndexChange = it }
        set(enabled) { element.IsEnabled = it }
        set(maxVisiblePips) { element.MaxVisiblePips = it }
        set(navigationButtons) {
            val visibility = if (it) PipsPagerButtonVisibility.Visible else PipsPagerButtonVisibility.Collapsed
            element.PreviousButtonVisibility = visibility
            element.NextButtonVisibility = visibility
        }
        // The page count must be in place before the index is synced, or WinUI clamps the index to the old count.
        set(numberOfPages) { count -> input.programmatic { element.NumberOfPages = count } }
        reconcile { input.sync(selectedPageIndex) }
    }
}

internal class PipsPagerNode(pager: XamlPipsPager) : ElementNode<XamlPipsPager>(pager, ChildSlot.None("PipsPager")) {
    var onSelectedPageIndexChange: (Int) -> Unit = {}

    val input = ControlledValue(read = { pager.SelectedPageIndex }, write = { pager.SelectedPageIndex = it })

    init {
        // SelectedIndexChanged is raised from the property change itself, so the index is current.
        onRelease(pager.SelectedIndexChanged.subscribe { _, _ -> input.userChanged(onSelectedPageIndexChange) })
    }
}
