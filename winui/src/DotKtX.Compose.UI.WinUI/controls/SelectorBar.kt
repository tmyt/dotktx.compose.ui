package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.Symbol
import Microsoft.UI.Xaml.Controls.SymbolIcon
import Microsoft.UI.Xaml.Controls.SelectorBar as XamlSelectorBar
import Microsoft.UI.Xaml.Controls.SelectorBarItem as XamlSelectorBarItem

/** One item of a [SelectorBar]: a label and an optional icon. */
data class SelectorBarEntry(val text: String, val icon: Symbol? = null)

/**
 * A row of mutually exclusive views to switch between (`SelectorBar`), controlled by [selectedIndex]
 * (-1 for no selection). Choosing an item calls [onSelectedIndexChange]; show the content for the
 * selected index below the bar.
 *
 * ```
 * SelectorBar(listOf(SelectorBarEntry("Recent", Symbol.Clock), SelectorBarEntry("Shared")), selectedIndex = view, onSelectedIndexChange = { view = it })
 * ```
 */
@Composable
fun SelectorBar(
    items: List<SelectorBarEntry>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = currentRecomposeScope
    Leaf({ SelectorBarNode(XamlSelectorBar()) }, modifier) {
        set(scope) { selection.scope = it }
        set(onSelectedIndexChange) { this.onSelectedIndexChange = it }
        set(items) { setItems(it) }
        reconcile { selection.sync(selectedIndex) }
    }
}

internal class SelectorBarNode(private val bar: XamlSelectorBar) : ElementNode<XamlSelectorBar>(bar, ChildSlot.None("SelectorBar")) {
    var onSelectedIndexChange: (Int) -> Unit = {}

    val selection = ControlledValue(
        read = { bar.SelectedItem?.let { bar.Items.IndexOf(it) } ?: -1 },
        write = { bar.SelectedItem = if (it >= 0 && it < bar.Items.Count) bar.Items[it] else null },
    )

    init {
        onRelease(bar.SelectionChanged.subscribe { _, _ -> selection.userChanged(onSelectedIndexChange) })
    }

    /** Replacing the items clears the selection; that is not a user change, and sync restores it. */
    fun setItems(items: List<SelectorBarEntry>) {
        selection.programmatic {
            bar.Items.Clear()
            for (entry in items) {
                val item = XamlSelectorBarItem()
                item.Text = entry.text
                if (entry.icon != null) item.Icon = SymbolIcon(entry.icon)
                bar.Items.Add(item)
            }
        }
    }
}
