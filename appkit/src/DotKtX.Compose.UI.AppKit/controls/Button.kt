package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import dotktx.compose.ui.annotations.EmitsOneElement
import AppKit.NSBezelStyle
import AppKit.NSButton
import AppKit.NSButtonType
import AppKit.NSCellStateValue

/**
 * A push button.
 *
 * ```
 * Button("Save", onClick = { save() })
 * ```
 */
@EmitsOneElement
@Composable
fun Button(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Leaf({ ButtonNode(newButton(NSButtonType.MomentaryPushIn, NSBezelStyle.Rounded)) }, modifier) {
        set(onClick) { this.onClick = it }
        set(enabled) { button.Enabled = it }
        set(title) { button.Title = it; invalidateLayout() }
    }
}

internal class ButtonNode(val button: NSButton) : ElementNode<NSButton>(button, ChildSlot.None("Button")) {
    var onClick: () -> Unit = {}

    init {
        onRelease(button.Activated.subscribe { _, _ -> onClick() })
    }
}

/**
 * A check box, controlled by [checked].
 *
 * ```
 * CheckBox("I agree", checked = agreed, onCheckedChange = { agreed = it })
 * ```
 */
@EmitsOneElement
@Composable
fun CheckBox(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scope = currentRecomposeScope
    Leaf({ CheckBoxNode(newButton(NSButtonType.Switch, null)) }, modifier) {
        set(scope) { input.scope = it }
        set(onCheckedChange) { this.onCheckedChange = it }
        set(enabled) { button.Enabled = it }
        set(title) { button.Title = it; invalidateLayout() }
        reconcile { input.sync(checked) }
    }
}

internal class CheckBoxNode(val button: NSButton) : ElementNode<NSButton>(button, ChildSlot.None("CheckBox")) {
    var onCheckedChange: (Boolean) -> Unit = {}

    val input = ControlledValue(
        read = { button.State == NSCellStateValue.On },
        write = { button.State = if (it) NSCellStateValue.On else NSCellStateValue.Off },
    )

    init {
        onRelease(button.Activated.subscribe { _, _ -> input.userChanged(onCheckedChange) })
    }
}

/**
 * A radio button, controlled by [selected]. Each radio button is controlled by the composition;
 * [onSelect] is called when the user picks it.
 *
 * ```
 * RadioButton("Small", selected = size == 0, onSelect = { size = 0 })
 * ```
 */
@EmitsOneElement
@Composable
fun RadioButton(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scope = currentRecomposeScope
    Leaf({ RadioButtonNode(newButton(NSButtonType.Radio, null)) }, modifier) {
        set(scope) { input.scope = it }
        set(onSelect) { this.onSelect = it }
        set(enabled) { button.Enabled = it }
        set(title) { button.Title = it; invalidateLayout() }
        reconcile { input.sync(selected) }
    }
}

internal class RadioButtonNode(val button: NSButton) : ElementNode<NSButton>(button, ChildSlot.None("RadioButton")) {
    var onSelect: () -> Unit = {}

    val input = ControlledValue(
        read = { button.State == NSCellStateValue.On },
        write = { button.State = if (it) NSCellStateValue.On else NSCellStateValue.Off },
    )

    init {
        onRelease(button.Activated.subscribe { _, _ -> input.userChanged { if (it) onSelect() } })
    }
}

private fun newButton(type: NSButtonType, bezel: NSBezelStyle?): NSButton {
    val button = NSButton()
    button.SetButtonType(type)
    if (bezel != null) button.BezelStyle = bezel
    return button
}
