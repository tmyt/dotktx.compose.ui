package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import kotlin.clr.byref
import Microsoft.UI.Text.TextGetOptions
import Microsoft.UI.Text.TextSetOptions
import Microsoft.UI.Xaml.Controls.RichEditBox as XamlRichEditBox

/**
 * Formatted text input (`RichEditBox`). Unlike [TextBox], it is **not controlled**: WinUI owns the
 * document, and the composition only seeds it and observes it.
 *
 * The document holds more than a string can describe: character and paragraph formatting, embedded
 * objects, the selection and the undo history. Writing a plain-text value back into it would drop
 * all of that, so there is no `text` parameter to keep in line with the application's state.
 *
 * - [initialText] is the plain text the document starts with. It is read once, when the box enters
 *   the composition; later changes are ignored. To replace the document, give the box a new
 *   identity, for example with `key(documentId) { RichEditBox(…) }`.
 * - [onTextChange] is called with the document's plain text after the user changes it. Paragraphs are
 *   separated by `\r`, as the document stores them; the paragraph mark that ends every document is
 *   not included. Formatting-only changes are not reported.
 *
 * ```
 * var notes by remember { mutableStateOf("") }
 * RichEditBox(initialText = loadedNotes, onTextChange = { notes = it })
 * ```
 */
@Composable
fun RichEditBox(
    modifier: Modifier = Modifier,
    initialText: String = "",
    onTextChange: (String) -> Unit = {},
    enabled: Boolean = true,
    placeholder: String = "",
) {
    Leaf({ RichEditBoxNode(XamlRichEditBox(), initialText) }, modifier) {
        set(onTextChange) { this.onTextChange = it }
        set(enabled) { element.IsEnabled = it }
        set(placeholder) { element.PlaceholderText = it }
    }
}

internal class RichEditBoxNode(box: XamlRichEditBox, initialText: String) :
    ElementNode<XamlRichEditBox>(box, ChildSlot.None("RichEditBox")) {
    var onTextChange: (String) -> Unit = {}

    /** The last text seen, so the seeding and formatting-only changes are not reported as edits. */
    private var lastText: String

    init {
        box.Document.SetText(TextSetOptions.None, initialText)
        // Read back rather than keep initialText: the document turns line breaks into `\r`.
        lastText = plainText(box)
        onRelease(box.TextChanged.subscribe { _, _ ->
            val text = plainText(box)
            if (text != lastText) {
                lastText = text
                onTextChange(text)
            }
        })
    }

    private fun plainText(box: XamlRichEditBox): String {
        var text = ""
        box.Document.GetText(TextGetOptions.None, byref(text))
        return text.removeSuffix("\r")
    }
}
