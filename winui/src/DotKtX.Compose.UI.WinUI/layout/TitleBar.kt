package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import Microsoft.UI.Xaml.Controls.TitleBar as XamlTitleBar

/**
 * A custom title bar for the current window (`TitleBar`). While it is in the composition the window
 * draws its content into the title bar area and uses this element as the title bar (drag area,
 * caption buttons next to it). Place it at the top of the window content, for example in an `Auto`
 * row of a [Grid].
 *
 * With [onBack] or [onPaneToggle] the back or pane toggle button is shown and calls it. [content] is
 * shown in the middle (typically a search box) and must emit at most one element.
 *
 * ```
 * Grid(rows = listOf(GridSize.Auto, GridSize.Star())) {
 *     TitleBar("Notes", Modifier.cell(0, 0), onBack = if (canGoBack) ::goBack else null)
 *     Page(Modifier.cell(1, 0))
 * }
 * ```
 */
@Composable
fun TitleBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    icon: Icon? = null,
    onBack: (() -> Unit)? = null,
    onPaneToggle: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    val window = LocalWindow.current
    // Created here rather than in the node factory, because the window needs the element itself.
    val bar = remember { XamlTitleBar() }
    Container({ TitleBarNode(bar) }, modifier, {
        set(title) { element.Title = it }
        set(subtitle) { element.Subtitle = it }
        set(icon) { element.IconSource = it?.toIconSource() }
        set(onBack) {
            this.onBack = it
            element.IsBackButtonVisible = it != null
            element.IsBackButtonEnabled = it != null
        }
        set(onPaneToggle) {
            this.onPaneToggle = it
            element.IsPaneToggleButtonVisible = it != null
        }
    }, content)

    DisposableEffect(window, bar) {
        window.ExtendsContentIntoTitleBar = true
        window.SetTitleBar(bar)
        onDispose {
            window.SetTitleBar(null)
            window.ExtendsContentIntoTitleBar = false
        }
    }
}

internal class TitleBarNode(bar: XamlTitleBar) :
    ElementNode<XamlTitleBar>(bar, ChildSlot.Single("TitleBar") { bar.Content = it }) {
    var onBack: (() -> Unit)? = null
    var onPaneToggle: (() -> Unit)? = null

    init {
        onRelease(bar.BackRequested.subscribe { _, _ -> onBack?.invoke() })
        onRelease(bar.PaneToggleRequested.subscribe { _, _ -> onPaneToggle?.invoke() })
    }
}
