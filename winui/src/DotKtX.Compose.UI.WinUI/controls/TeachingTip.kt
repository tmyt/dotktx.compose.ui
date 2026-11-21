package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.TeachingTipPlacementMode
import Microsoft.UI.Xaml.Controls.TeachingTip as XamlTeachingTip

/**
 * A callout that explains part of the UI (`TeachingTip`), shown while [isOpen] is true. It points at
 * the element that [target] is attached to, or is placed against the window when [target] is null.
 *
 * The close button (the X, or the button labelled [closeButtonText]) and, with [lightDismiss], a
 * click outside the tip close it and call [onDismissRequest]; the application sets [isOpen] to false
 * in response. If it keeps [isOpen] true, the tip opens again. The action button is shown when
 * [actionButtonText] is not empty and calls [onActionClick]. [content] is shown under the subtitle
 * and must emit at most one element.
 *
 * The composable places the tip in the layout where it is called, without taking up space; WinUI
 * shows the tip itself in a popup.
 *
 * ```
 * val anchor = rememberPopupAnchor()
 * Button(onClick = ::save, Modifier.popupAnchor(anchor)) { TextBlock("Save") }
 * TeachingTip(
 *     isOpen = showTip,
 *     onDismissRequest = { showTip = false },
 *     title = "Save often",
 *     subtitle = "Your work is saved to the cloud.",
 *     target = anchor,
 * )
 * ```
 */
@Composable
fun TeachingTip(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    target: PopupAnchor? = null,
    actionButtonText: String = "",
    onActionClick: () -> Unit = {},
    closeButtonText: String = "",
    lightDismiss: Boolean = false,
    placement: TeachingTipPlacementMode = TeachingTipPlacementMode.Auto,
    content: @Composable () -> Unit = {},
) {
    val scope = currentRecomposeScope
    val targetElement = target?.element
    Container({ TeachingTipNode(XamlTeachingTip()) }, modifier, {
        set(scope) { input.scope = it }
        set(onDismissRequest) { this.onDismissRequest = it }
        set(onActionClick) { this.onActionClick = it }
        set(title) { element.Title = it }
        set(subtitle) { element.Subtitle = it }
        set(actionButtonText) {
            if (it.isEmpty()) element.ClearValue(XamlTeachingTip.ActionButtonContentProperty) else element.ActionButtonContent = it
        }
        set(closeButtonText) {
            if (it.isEmpty()) element.ClearValue(XamlTeachingTip.CloseButtonContentProperty) else element.CloseButtonContent = it
        }
        set(lightDismiss) { element.IsLightDismissEnabled = it }
        set(placement) { element.PreferredPlacement = it }
        set(targetElement) { element.Target = it }
        reconcile { sync(isOpen) }
    }, content)
}

internal class TeachingTipNode(tip: XamlTeachingTip) :
    ElementNode<XamlTeachingTip>(tip, ChildSlot.Single("TeachingTip") { tip.Content = it }) {
    var onDismissRequest: () -> Unit = {}
    var onActionClick: () -> Unit = {}

    val input = ControlledValue(read = { tip.IsOpen }, write = { tip.IsOpen = it })

    /**
     * True from Closing until Closed. Opening the tip again while it animates closed leaves IsOpen
     * true with the popup hidden, so the composed value is applied once the close has finished.
     */
    private var closing = false

    init {
        // The close button and light dismiss set IsOpen to false before Closing is raised. Cancelling
        // Closing would keep the tip open, but after a cancelled close the tip no longer closes
        // reliably (a later IsOpen = false leaves the popup on screen), so the close is reported
        // instead, and the tip opens again if the application keeps isOpen true.
        onRelease(tip.Closing.subscribe { _, _ ->
            closing = true
            input.userChanged { open -> if (!open) onDismissRequest() }
        })
        onRelease(tip.Closed.subscribe { _, _ ->
            closing = false
            input.scope?.invalidate()
        })
        onRelease(tip.ActionButtonClick.subscribe { _, _ -> onActionClick() })
    }

    fun sync(open: Boolean) {
        if (!closing) input.sync(open)
    }
}
