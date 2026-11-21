package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import Microsoft.UI.Xaml.FrameworkElement

/**
 * Names the element that a [Flyout] or [TeachingTip] points at. Create it with
 * [rememberPopupAnchor], attach it to the target with [Modifier.popupAnchor], and pass it to the popup.
 *
 * The anchor and the popup can be in different parts of the composition, or in different
 * compositions (inside a [LazyItems] row, for example). The anchor refers to the element it was last
 * attached to; a popup whose anchor is not attached yet is shown once it is.
 *
 * ```
 * val anchor = rememberPopupAnchor()
 * Button(onClick = { showHelp = true }, Modifier.popupAnchor(anchor)) { TextBlock("Help") }
 * if (showHelp) Flyout(anchor, onDismissRequest = { showHelp = false }) { TextBlock("Press F1 any time.") }
 * ```
 */
@Stable
class PopupAnchor {
    /** Snapshot state, so popups that read it recompose when the anchor is attached. */
    internal var element: FrameworkElement? by mutableStateOf(null)
}

/** Creates a [PopupAnchor] that survives recomposition. */
@Composable
fun rememberPopupAnchor(): PopupAnchor = remember { PopupAnchor() }

/** Makes this element the target of popups that use [anchor]. */
fun Modifier.popupAnchor(anchor: PopupAnchor): Modifier = this then PopupAnchorModifier(anchor)

private data class PopupAnchorModifier(val anchor: PopupAnchor) : ElementModifier {
    override val key: Any get() = PopupAnchorModifier::class

    override fun apply(element: FrameworkElement) {
        anchor.element = element
    }

    override fun reset(element: FrameworkElement) {
        if (anchor.element === element) anchor.element = null
    }
}

/**
 * Runs [action] once [element] has a `XamlRoot` (it is in a live visual tree), which popups need to
 * be shown. The returned handle cancels a pending run.
 */
internal fun whenInTree(element: FrameworkElement, action: () -> Unit): AutoCloseable {
    if (element.XamlRoot != null) {
        action()
        return AutoCloseable {}
    }
    var loaded: AutoCloseable? = null
    loaded = element.Loaded.subscribe { _, _ ->
        loaded?.close()
        loaded = null
        action()
    }
    return AutoCloseable {
        loaded?.close()
        loaded = null
    }
}
