package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.InfoBarSeverity
import Microsoft.UI.Xaml.Controls.InfoBar as XamlInfoBar
import Microsoft.UI.Xaml.Controls.ProgressRing as XamlProgressRing

/**
 * A spinning progress indicator (`ProgressRing`). It spins while [indeterminate] is true; otherwise it
 * shows [value] out of [maximum]. Nothing is drawn while [active] is false.
 */
@Composable
fun ProgressRing(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    indeterminate: Boolean = true,
    value: Double = 0.0,
    maximum: Double = 100.0,
) {
    Leaf({ ElementNode(XamlProgressRing(), ChildSlot.None("ProgressRing")) }, modifier) {
        set(active) { element.IsActive = it }
        set(indeterminate) { element.IsIndeterminate = it }
        set(maximum) { element.Maximum = it }
        set(value) { element.Value = it }
    }
}

/** Importance of an [InfoBar] message, which picks its icon and color. */
enum class Severity { Informational, Success, Warning, Error }

/**
 * An inline notification (`InfoBar`), shown while [isOpen] is true. With [onDismiss] it has a close
 * button that calls [onDismiss]; the bar closes when the application sets [isOpen] to false.
 *
 * ```
 * InfoBar(isOpen = saved, title = "Saved", message = "All changes are saved.", severity = Severity.Success,
 *     onDismiss = { saved = false })
 * ```
 */
@Composable
fun InfoBar(
    isOpen: Boolean,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    severity: Severity = Severity.Informational,
    onDismiss: (() -> Unit)? = null,
) {
    val scope = currentRecomposeScope
    Leaf({ InfoBarNode(XamlInfoBar()) }, modifier) {
        set(scope) { input.scope = it }
        set(onDismiss) { this.onDismiss = it; element.IsClosable = it != null }
        set(title) { element.Title = it }
        set(message) { element.Message = it }
        set(severity) {
            element.Severity = when (it) {
                Severity.Informational -> InfoBarSeverity.Informational
                Severity.Success -> InfoBarSeverity.Success
                Severity.Warning -> InfoBarSeverity.Warning
                Severity.Error -> InfoBarSeverity.Error
            }
        }
        reconcile { input.sync(isOpen) }
    }
}

internal class InfoBarNode(bar: XamlInfoBar) : ElementNode<XamlInfoBar>(bar, ChildSlot.None("InfoBar")) {
    var onDismiss: (() -> Unit)? = null

    val input = ControlledValue(read = { bar.IsOpen }, write = { bar.IsOpen = it })

    init {
        // The close button closes the bar before Closed is raised; report it so the application
        // can update its state (or reopen the bar by leaving isOpen true).
        onRelease(bar.Closed.subscribe { _, _ -> input.userChanged { open -> if (!open) onDismiss?.invoke() } })
    }
}
