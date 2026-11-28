package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import AppKit.NSPopUpButton
import System.IntPtr

/**
 * A pop-up menu button (`NSPopUpButton`) listing [items], controlled by [selectedIndex].
 *
 * ```
 * PopUpButton(items = listOf("Small", "Large"), selectedIndex = size, onSelectedIndexChange = { size = it })
 * ```
 */
@Composable
fun PopUpButton(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scope = currentRecomposeScope
    Leaf({ PopUpButtonNode(NSPopUpButton().also { it.PullsDown = false }) }, modifier) {
        set(scope) { input.scope = it }
        set(onSelectedIndexChange) { this.onSelectedIndexChange = it }
        set(enabled) { popUp.Enabled = it }
        set(items) {
            input.programmatic {
                popUp.RemoveAllItems()
                popUp.AddItems(it.toTypedArray())
            }
            invalidateLayout()
        }
        reconcile { input.sync(selectedIndex) }
    }
}

internal class PopUpButtonNode(val popUp: NSPopUpButton) : ElementNode<NSPopUpButton>(popUp, ChildSlot.None("PopUpButton")) {
    var onSelectedIndexChange: (Int) -> Unit = {}

    val input = ControlledValue(
        read = { popUp.IndexOfSelectedItem.ToInt32() },
        write = { popUp.SelectItem(IntPtr(it)) },
    )

    init {
        onRelease(popUp.Activated.subscribe { _, _ -> input.userChanged(onSelectedIndexChange) })
    }
}
