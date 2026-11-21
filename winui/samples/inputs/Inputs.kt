import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dotktx.compose.ui.winui.AutoSuggestBox
import dotktx.compose.ui.winui.Border
import dotktx.compose.ui.winui.Button
import dotktx.compose.ui.winui.ColorPicker
import dotktx.compose.ui.winui.MenuEntry
import dotktx.compose.ui.winui.Modifier
import dotktx.compose.ui.winui.RatingControl
import dotktx.compose.ui.winui.RepeatButton
import dotktx.compose.ui.winui.RichEditBox
import dotktx.compose.ui.winui.ScrollViewer
import dotktx.compose.ui.winui.SplitButton
import dotktx.compose.ui.winui.StackPanel
import dotktx.compose.ui.winui.TextBlock
import dotktx.compose.ui.winui.ToggleButton
import dotktx.compose.ui.winui.ToggleSplitButton
import dotktx.compose.ui.winui.Window
import dotktx.compose.ui.winui.application
import dotktx.compose.ui.winui.height
import dotktx.compose.ui.winui.margin
import dotktx.compose.ui.winui.rememberWindowState
import dotktx.compose.ui.winui.size
import dotktx.compose.ui.winui.testTag
import dotktx.compose.ui.winui.width
import Microsoft.UI.Colors
import Microsoft.UI.Xaml.Controls.Orientation
import Microsoft.UI.Xaml.Controls.Symbol
import Microsoft.UI.Xaml.Media.SolidColorBrush
import Windows.UI.Color

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Inputs", state = rememberWindowState(width = 760.0, height = 900.0)) {
        ScrollViewer {
            StackPanel(Modifier.margin(24.0), spacing = 12.0) {
                TextBlock("Buttons", fontSize = 20.0)
                Buttons()
                TextBlock("Split buttons", fontSize = 20.0)
                SplitButtons()
                TextBlock("Rating", fontSize = 20.0)
                Rating()
                TextBlock("Auto-suggest", fontSize = 20.0)
                Suggest()
                TextBlock("Rich text", fontSize = 20.0)
                RichText()
                TextBlock("Color", fontSize = 20.0)
                ColorChooser()
            }
        }
    }
}

private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

@Composable
private fun Row(content: @Composable () -> Unit) {
    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0, content = content)
}

@Composable
private fun Buttons() {
    var bold by remember { mutableStateOf(false) }
    var repeats by remember { mutableIntStateOf(0) }

    Row {
        ToggleButton(bold, { bold = it }, Modifier.testTag("bold")) { TextBlock("Bold") }
        Button({ bold = !bold }, Modifier.testTag("boldFromApp")) { TextBlock("Toggle bold from the app") }
    }
    TextBlock("bold=${yesNo(bold)}", Modifier.testTag("boldEcho"))

    // The application never accepts the change, so the button keeps showing "off".
    ToggleButton(false, {}, Modifier.testTag("locked")) { TextBlock("Locked") }

    Row {
        RepeatButton({ repeats++ }, Modifier.testTag("repeat"), delayMillis = 400, intervalMillis = 100) { TextBlock("Hold to repeat") }
        TextBlock("repeats=$repeats", Modifier.testTag("repeatEcho"))
    }
}

@Composable
private fun SplitButtons() {
    var action by remember { mutableStateOf("none") }
    var listOn by remember { mutableStateOf(false) }
    var listStyle by remember { mutableStateOf("bullets") }

    SplitButton(
        onClick = { action = "paste" },
        menu = listOf(
            MenuEntry.Item("Paste as text") { action = "paste:text" },
            MenuEntry.Item("Paste as image") { action = "paste:image" },
        ),
        modifier = Modifier.testTag("split"),
    ) { TextBlock("Paste") }
    TextBlock("action=$action", Modifier.testTag("splitEcho"))

    Row {
        ToggleSplitButton(
            checked = listOn,
            onCheckedChange = { listOn = it },
            menu = listOf(
                MenuEntry.Item("Bullets") { listStyle = "bullets"; listOn = true },
                MenuEntry.Item("Numbers") { listStyle = "numbers"; listOn = true },
            ),
            modifier = Modifier.testTag("list"),
        ) { TextBlock("List") }
        Button({ listOn = !listOn }, Modifier.testTag("listFromApp")) { TextBlock("Toggle list from the app") }
    }
    TextBlock("list=${yesNo(listOn)} style=$listStyle", Modifier.testTag("listEcho"))
}

@Composable
private fun Rating() {
    var stars by remember { mutableDoubleStateOf(Double.NaN) }

    Row {
        RatingControl(stars, { stars = it }, Modifier.testTag("rating"))
        Button({ stars = 4.0 }, Modifier.testTag("rateFour")) { TextBlock("Rate 4") }
        Button({ stars = Double.NaN }, Modifier.testTag("clearRating")) { TextBlock("Clear") }
    }
    RatingControl(stars, {}, Modifier.testTag("ratingReadOnly"), maxRating = 10, isReadOnly = true)
    TextBlock("rating=${if (stars.isNaN()) "none" else stars.toInt().toString()}", Modifier.testTag("ratingEcho"))
}

private val fruits = listOf("Apple", "Apricot", "Banana", "Blueberry", "Cherry")

@Composable
private fun Suggest() {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }

    // Digits are rejected, so the box keeps showing the accepted text.
    AutoSuggestBox(
        text = query,
        onTextChange = { if (it.none { c -> c in '0'..'9' }) query = it },
        modifier = Modifier.width(320.0).testTag("suggest"),
        suggestions = if (query.isEmpty()) emptyList() else fruits.filter { it.startsWith(query, ignoreCase = true) },
        onSuggestionChosen = { chosen = it; query = it },
        onQuerySubmitted = { typed, suggestion -> submitted = if (suggestion != null) "suggestion:$suggestion" else typed },
        placeholder = "Search fruit",
        queryIcon = Symbol.Find,
    )
    Button({ query = "Cherry" }, Modifier.testTag("suggestFromApp")) { TextBlock("Set Cherry from the app") }
    TextBlock("query=$query chosen=$chosen submitted=$submitted", Modifier.testTag("suggestEcho"))
}

@Composable
private fun RichText() {
    var text by remember { mutableStateOf("Hello") }
    RichEditBox(Modifier.width(320.0).height(80.0).testTag("rich"), initialText = "Hello", onTextChange = { text = it })
    TextBlock("rich=$text", Modifier.testTag("richEcho"))
}

private fun hex(color: Color): String {
    fun part(value: Int) = (value and 0xFF).toString(16).uppercase().padStart(2, '0')
    return "#" + part(color.A.toInt()) + part(color.R.toInt()) + part(color.G.toInt()) + part(color.B.toInt())
}

@Composable
private fun ColorChooser() {
    var color by remember { mutableStateOf(Colors.Red) }

    Row {
        Button({ color = Colors.Blue }, Modifier.testTag("blueFromApp")) { TextBlock("Blue from the app") }
        Border(Modifier.size(32.0, 32.0), background = SolidColorBrush(color)) { TextBlock("") }
        TextBlock("color=${hex(color)}", Modifier.testTag("colorEcho"))
    }
    ColorPicker(color, { color = it })
}
