package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.Controls.AutoSuggestionBoxTextChangeReason
import System.Collections.Generic.List as ClrList
import Microsoft.UI.Xaml.Controls.AutoSuggestBox as XamlAutoSuggestBox

/**
 * A text box with a list of suggestions below it (`AutoSuggestBox`), controlled by [text].
 *
 * - [onTextChange] reports what the user types. The box shows [text]: if the application does not
 *   update its state with the new value, the edit is undone.
 * - [suggestions] is the list shown below the box; the application usually filters it by [text].
 * - Clicking a suggestion or moving through the list with the arrow keys calls
 *   [onSuggestionChosen]. The box shows the suggestion only when the application puts it in [text].
 * - Enter, the query button ([queryIcon]) or clicking a suggestion calls [onQuerySubmitted] with the
 *   box's text and the suggestion that was clicked or highlighted, or `null` when the user submitted
 *   typed text. The suggestion is passed here as well because WinUI does not always report it
 *   through [onSuggestionChosen] first: invoking an entry through UI Automation without selecting it
 *   submits it directly.
 *
 * ```
 * var query by remember { mutableStateOf("") }
 * AutoSuggestBox(
 *     text = query,
 *     onTextChange = { query = it },
 *     suggestions = fruits.filter { it.startsWith(query, ignoreCase = true) },
 *     onSuggestionChosen = { query = it },
 *     onQuerySubmitted = { typed, suggestion -> search(suggestion ?: typed) },
 *     placeholder = "Search fruit",
 *     queryIcon = Icon.Symbol(Symbol.Find),
 * )
 * ```
 */
@EmitsOneElement
@Composable
fun AutoSuggestBox(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
    onSuggestionChosen: (String) -> Unit = {},
    onQuerySubmitted: (query: String, suggestion: String?) -> Unit = { _, _ -> },
    enabled: Boolean = true,
    placeholder: String = "",
    queryIcon: Icon? = null,
) {
    val scope = currentRecomposeScope
    Leaf({ AutoSuggestBoxNode(XamlAutoSuggestBox()) }, modifier) {
        set(scope) { input.scope = it }
        set(onTextChange) { this.onTextChange = it }
        set(onSuggestionChosen) { this.onSuggestionChosen = it }
        set(onQuerySubmitted) { this.onQuerySubmitted = it }
        set(enabled) { element.IsEnabled = it }
        set(placeholder) { element.PlaceholderText = it }
        set(queryIcon) { if (it == null) element.ClearValue(XamlAutoSuggestBox.QueryIconProperty) else element.QueryIcon = it.toIconElement() }
        // A new ItemsSource, not edits of Items: the box opens its list for a new source, while
        // changes made through Items leave it closed.
        set(suggestions) { list ->
            val source = ClrList<String>()
            for (item in list) source.Add(item)
            element.ItemsSource = source
        }
        reconcile { input.sync(text) }
    }
}

/**
 * Only `TextChanged` tells user input from other changes, and it is not raised synchronously with the
 * change of `Text`: it follows the inner text box's own `TextChanged`, which WinUI raises after the
 * edit. From the change of `Text` until `TextChanged` the new text is pending and is treated like an
 * IME composition: it is not overwritten, so a recomposition in between cannot sync the old value
 * over the edit before it is reported.
 */
internal class AutoSuggestBoxNode(box: XamlAutoSuggestBox) :
    ElementNode<XamlAutoSuggestBox>(box, ChildSlot.None("AutoSuggestBox")) {
    var onTextChange: (String) -> Unit = {}
    var onSuggestionChosen: (String) -> Unit = {}
    var onQuerySubmitted: (String, String?) -> Unit = { _, _ -> }

    private var writing = false

    val input = ControlledValue(read = { box.Text }, write = { value ->
        writing = true
        try {
            box.Text = value
        } finally {
            writing = false
        }
    })

    init {
        val token = box.RegisterPropertyChangedCallback(XamlAutoSuggestBox.TextProperty) { _, _ ->
            if (!writing) input.composing = true
        }
        onRelease(AutoCloseable { box.UnregisterPropertyChangedCallback(XamlAutoSuggestBox.TextProperty, token) })
        onRelease(box.TextChanged.subscribe { _, e ->
            if (e.Reason == AutoSuggestionBoxTextChangeReason.UserInput) input.userChanged(onTextChange)
            // Ending the pending state invalidates the scope, so text the application did not take
            // (a rejected edit, a suggestion it did not put in its state) is replaced by its own.
            input.composing = false
        })
        onRelease(box.SuggestionChosen.subscribe { _, e -> (e.SelectedItem as? String)?.let { onSuggestionChosen(it) } })
        onRelease(box.QuerySubmitted.subscribe { _, e -> onQuerySubmitted(e.QueryText, e.ChosenSuggestion as? String) })
    }
}
