package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import AppKit.NSSwitch
import System.IntPtr

/**
 * An on/off switch (`NSSwitch`), controlled by [isOn].
 *
 * ```
 * Switch(isOn = wifi, onToggle = { wifi = it })
 * ```
 */
@Composable
fun Switch(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scope = currentRecomposeScope
    Leaf({ SwitchNode(NSSwitch()) }, modifier) {
        set(scope) { input.scope = it }
        set(onToggle) { this.onToggle = it }
        set(enabled) { toggle.Enabled = it }
        reconcile { input.sync(isOn) }
    }
}

internal class SwitchNode(val toggle: NSSwitch) : ElementNode<NSSwitch>(toggle, ChildSlot.None("Switch")) {
    var onToggle: (Boolean) -> Unit = {}

    val input = ControlledValue(
        read = { toggle.State.ToInt32() == 1 },
        write = { toggle.State = IntPtr(if (it) 1 else 0) },
    )

    init {
        onRelease(toggle.Activated.subscribe { _, _ -> input.userChanged(onToggle) })
    }
}
