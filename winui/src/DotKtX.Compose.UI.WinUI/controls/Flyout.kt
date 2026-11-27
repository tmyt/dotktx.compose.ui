package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import dotktx.compose.ui.annotations.AtMostOneElementContent
import Microsoft.UI.Xaml.Controls.Primitives.FlyoutPlacementMode
import Microsoft.UI.Xaml.Controls.Flyout as XamlFlyout

/**
 * A light-dismiss popup with composable content (`Flyout`), shown next to the element that [anchor]
 * is attached to while this composable is in the composition.
 *
 * Clicking outside the flyout or pressing Esc does not close it: it calls [onDismissRequest], and
 * the flyout closes when the application removes it from the composition. [content] is composed in a
 * composition of its own, whose locals and invalidation come from the caller, and must emit at most
 * one element.
 *
 * ```
 * val anchor = rememberPopupAnchor()
 * Button(onClick = { editing = true }, Modifier.popupAnchor(anchor)) { TextBlock("Rename") }
 * if (editing) {
 *     Flyout(anchor, onDismissRequest = { editing = false }) {
 *         TextBox(name, onTextChange = { name = it })
 *     }
 * }
 * ```
 */
@Composable
fun Flyout(
    anchor: PopupAnchor,
    onDismissRequest: () -> Unit,
    placement: FlyoutPlacementMode = FlyoutPlacementMode.Top,
    @AtMostOneElementContent content: @Composable () -> Unit,
) {
    val parent = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val flyout = remember { XamlFlyout() }
    val target = anchor.element

    SideEffect {
        flyout.Placement = placement
    }

    DisposableEffect(flyout) {
        var closingFromComposition = false
        val closing = flyout.Closing.subscribe { _, e ->
            if (!closingFromComposition) {
                e.Cancel = true
                currentOnDismissRequest()
            }
        }
        val composition = Composition(WinUIApplier(FlyoutRoot(flyout)), parent)
        composition.setContent { currentContent() }
        onDispose {
            closingFromComposition = true
            flyout.Hide()
            closing.close()
            composition.dispose()
        }
    }

    DisposableEffect(flyout, target) {
        val pending = if (target != null) whenInTree(target) { flyout.ShowAt(target) } else null
        onDispose { pending?.close() }
    }
}

/** Root node of a flyout's composition; its single child becomes `Flyout.Content`. */
private class FlyoutRoot(flyout: XamlFlyout) : Node(ChildSlot.Single("Flyout") { flyout.Content = it })
