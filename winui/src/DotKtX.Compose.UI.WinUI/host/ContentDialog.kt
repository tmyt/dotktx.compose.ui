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
import Microsoft.UI.Xaml.FrameworkElement
import Microsoft.UI.Xaml.Controls.ContentDialogButton
import Microsoft.UI.Xaml.Controls.ContentDialogResult
import Microsoft.UI.Xaml.Controls.ContentDialog as XamlContentDialog

/**
 * A modal dialog over the current window (`ContentDialog`), shown while this composable is in the
 * composition.
 *
 * The dialog's buttons do not close it: they call [onPrimaryClick], [onSecondaryClick] or
 * [onDismissRequest] (close button, Esc), and the dialog closes when the application removes it from
 * the composition. A button is shown only when its text is not empty. [content] must emit at most one
 * element. WinUI shows one `ContentDialog` per window at a time. It works inside [Window] and
 * [ComposeHost].
 *
 * ```
 * if (confirmDelete) {
 *     ContentDialog(
 *         onDismissRequest = { confirmDelete = false },
 *         title = "Delete the file?",
 *         primaryButtonText = "Delete",
 *         onPrimaryClick = { delete(); confirmDelete = false },
 *         closeButtonText = "Cancel",
 *     ) { TextBlock("This cannot be undone.") }
 * }
 * ```
 */
@Composable
fun ContentDialog(
    onDismissRequest: () -> Unit,
    title: String = "",
    primaryButtonText: String = "",
    onPrimaryClick: () -> Unit = {},
    secondaryButtonText: String = "",
    onSecondaryClick: () -> Unit = {},
    closeButtonText: String = "",
    defaultButton: DialogButton = DialogButton.None,
    @AtMostOneElementContent content: @Composable () -> Unit,
) {
    val xamlRootSource = LocalXamlRootSource.current
    val parent = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentOnPrimaryClick by rememberUpdatedState(onPrimaryClick)
    val currentOnSecondaryClick by rememberUpdatedState(onSecondaryClick)
    val dialog = remember { XamlContentDialog() }

    SideEffect {
        dialog.Title = title
        dialog.PrimaryButtonText = primaryButtonText
        dialog.SecondaryButtonText = secondaryButtonText
        dialog.CloseButtonText = closeButtonText
        dialog.DefaultButton = when (defaultButton) {
            DialogButton.None -> ContentDialogButton.None
            DialogButton.Primary -> ContentDialogButton.Primary
            DialogButton.Secondary -> ContentDialogButton.Secondary
            DialogButton.Close -> ContentDialogButton.Close
        }
    }

    DisposableEffect(dialog) {
        var closingFromComposition = false
        val closing = dialog.Closing.subscribe { _, e ->
            if (!closingFromComposition) {
                e.Cancel = true
                when (e.Result) {
                    ContentDialogResult.Primary -> currentOnPrimaryClick()
                    ContentDialogResult.Secondary -> currentOnSecondaryClick()
                    else -> currentOnDismissRequest()
                }
            }
        }
        val composition = Composition(WinUIApplier(DialogRoot(dialog)), parent)
        composition.setContent { currentContent() }

        // A dialog needs the XamlRoot of the window it covers, which exists once the window content is loaded.
        val windowContent = xamlRootSource()
            ?: throw IllegalStateException("ContentDialog needs window content to show over")
        var loaded: AutoCloseable? = null
        if (windowContent.XamlRoot != null) {
            show(dialog, windowContent)
        } else {
            loaded = windowContent.Loaded.subscribe { _, _ ->
                loaded?.close()
                loaded = null
                show(dialog, windowContent)
            }
        }
        onDispose {
            loaded?.close()
            closingFromComposition = true
            dialog.Hide()
            closing.close()
            composition.dispose()
        }
    }
}

/** The button of a [ContentDialog] that Enter activates. */
enum class DialogButton { None, Primary, Secondary, Close }

private fun show(dialog: XamlContentDialog, windowContent: FrameworkElement) {
    dialog.XamlRoot = windowContent.XamlRoot
    dialog.ShowAsync()
}

/** Root node of a dialog's composition; its single child becomes `ContentDialog.Content`. */
private class DialogRoot(dialog: XamlContentDialog) : Node(ChildSlot.Single("ContentDialog") { dialog.Content = it })
