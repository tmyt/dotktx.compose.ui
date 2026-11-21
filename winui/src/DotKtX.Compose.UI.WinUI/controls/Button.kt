package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import Microsoft.UI.Xaml.Controls.Primitives.ButtonBase
import Microsoft.UI.Xaml.Controls.Button as XamlButton
import Microsoft.UI.Xaml.Controls.HyperlinkButton as XamlHyperlinkButton

/**
 * A push button (`Button`). [content] must emit exactly one element, which becomes `Button.Content`.
 *
 * ```
 * Button(onClick = { count++ }) { TextBlock("$count") }
 * ```
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Container({ ButtonNode(XamlButton(), "Button") }, modifier, {
        set(onClick) { this.onClick = it }
        set(enabled) { element.IsEnabled = it }
    }, content)
}

/**
 * A button that looks like a link (`HyperlinkButton`). [content] must emit exactly one element.
 *
 * ```
 * HyperlinkButton(onClick = { showHelp = true }) { TextBlock("Help") }
 * ```
 */
@Composable
fun HyperlinkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Container({ ButtonNode(XamlHyperlinkButton(), "HyperlinkButton") }, modifier, {
        set(onClick) { this.onClick = it }
        set(enabled) { element.IsEnabled = it }
    }, content)
}

/**
 * `Click` is subscribed once, when the element is created, and calls the latest [onClick].
 * Subscribing on every recomposition would stack handlers and run one click several times.
 */
internal class ButtonNode<B : ButtonBase>(button: B, owner: String) :
    ElementNode<B>(button, ChildSlot.Single(owner) { button.Content = it }) {
    var onClick: () -> Unit = {}

    init {
        onRelease(button.Click.subscribe { _, _ -> onClick() })
    }
}
