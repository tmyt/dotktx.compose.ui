package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.Controls.TextBox as XamlTextBox

/**
 * Single-line text input (`TextBox`), controlled by [text].
 *
 * Every edit calls [onTextChange]. The box shows [text]: if the application does not update its
 * state with the new value, the edit is undone.
 *
 * ```
 * var name by remember { mutableStateOf("") }
 * TextBox(text = name, onTextChange = { name = it })
 * ```
 */
@EmitsOneElement
@Composable
fun TextBox(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    val scope = currentRecomposeScope
    Leaf({ TextBoxNode(XamlTextBox()) }, modifier) {
        set(scope) { input.scope = it }
        set(onTextChange) { this.onTextChange = it }
        set(enabled) { element.IsEnabled = it }
        set(placeholder) { element.PlaceholderText = it }
        reconcile { input.sync(text) }
    }
}

internal class TextBoxNode(box: XamlTextBox) : ElementNode<XamlTextBox>(box, ChildSlot.None("TextBox")) {
    var onTextChange: (String) -> Unit = {}

    val input = ControlledValue(read = { box.Text }, write = { value ->
        // Setting Text moves the caret to the start; keep it where the user was typing.
        val caret = box.SelectionStart
        box.Text = value
        box.SelectionStart = minOf(caret, value.length)
    })

    init {
        // TextChanging is raised synchronously with the edit. TextChanged arrives later, and a
        // recomposition in between would sync the old value over the edit before it is reported.
        onRelease(box.TextChanging.subscribe { _, _ -> input.userChanged(onTextChange) })
        onRelease(box.TextCompositionStarted.subscribe { _, _ -> input.composing = true })
        onRelease(box.TextCompositionEnded.subscribe { _, _ -> input.composing = false })
    }
}
