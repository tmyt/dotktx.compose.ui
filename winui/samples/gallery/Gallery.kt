import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dotktx.compose.ui.winui.Icon
import dotktx.compose.ui.winui.Alignment
import dotktx.compose.ui.winui.Border
import dotktx.compose.ui.winui.Button
import dotktx.compose.ui.winui.CheckBox
import dotktx.compose.ui.winui.ComboBox
import dotktx.compose.ui.winui.ContentDialog
import dotktx.compose.ui.winui.DialogButton
import dotktx.compose.ui.winui.DropDownButton
import dotktx.compose.ui.winui.MenuEntry
import dotktx.compose.ui.winui.contextMenu
import dotktx.compose.ui.winui.Grid
import dotktx.compose.ui.winui.HyperlinkButton
import dotktx.compose.ui.winui.InfoBar
import dotktx.compose.ui.winui.LazyItems
import dotktx.compose.ui.winui.NumberBox
import dotktx.compose.ui.winui.PasswordBox
import dotktx.compose.ui.winui.ProgressRing
import dotktx.compose.ui.winui.RadioButton
import dotktx.compose.ui.winui.Severity
import dotktx.compose.ui.winui.GridSize
import dotktx.compose.ui.winui.Modifier
import dotktx.compose.ui.winui.Canvas
import dotktx.compose.ui.winui.ProgressBar
import dotktx.compose.ui.winui.ScrollViewer
import dotktx.compose.ui.winui.Slider
import dotktx.compose.ui.winui.StackPanel
import dotktx.compose.ui.winui.TextBlock
import dotktx.compose.ui.winui.TextBox
import dotktx.compose.ui.winui.ToggleSwitch
import dotktx.compose.ui.winui.Window
import dotktx.compose.ui.winui.WindowPlacement
import dotktx.compose.ui.winui.WindowPosition
import dotktx.compose.ui.winui.WindowState
import dotktx.compose.ui.winui.rememberWindowState
import dotktx.compose.ui.winui.align
import dotktx.compose.ui.winui.application
import dotktx.compose.ui.winui.height
import dotktx.compose.ui.winui.margin
import dotktx.compose.ui.winui.padding
import dotktx.compose.ui.winui.size
import dotktx.compose.ui.winui.semantics
import dotktx.compose.ui.winui.testTag
import dotktx.compose.ui.winui.toolTip
import dotktx.compose.ui.winui.width
import dotktx.compose.ui.winui.widthIn
import Microsoft.UI.Colors
import Microsoft.UI.Xaml.Controls.Orientation
import Microsoft.UI.Xaml.Controls.Symbol
import Microsoft.UI.Xaml.Media.SolidColorBrush
import System.STAThreadAttribute

@STAThreadAttribute
fun main() = application {
    var showSettings by remember { mutableStateOf(false) }
    val mainWindow = rememberWindowState(width = 720.0, height = 640.0, position = WindowPosition.Center)
    Window(onCloseRequest = ::exitApplication, title = "Gallery", state = mainWindow) {
        ScrollViewer(Modifier.testTag("galleryScroll")) {
            StackPanel(Modifier.margin(24.0), spacing = 12.0) {
                TextBlock("Window", fontSize = 20.0)
                WindowControls(mainWindow)
                TextBlock("Inputs", fontSize = 20.0)
                Inputs()
                TextBlock("More controls", fontSize = 20.0)
                MoreControls()
                TextBlock("Keyed list", fontSize = 20.0)
                KeyedList()
                TextBlock("Lazy list", fontSize = 20.0)
                LazyList()
                KeyedLazyList()
                TextBlock("Layout", fontSize = 20.0)
                Layout()
                Button(onClick = { showSettings = true }, modifier = Modifier.testTag("openSettings")) {
                    TextBlock("Open settings")
                }
            }
        }
    }
    if (showSettings) {
        Window(onCloseRequest = { showSettings = false }, title = "Settings") {
            TextBlock("Settings window", Modifier.align(Alignment.Center).testTag("settingsText"))
        }
    }
}

private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

@Composable
private fun WindowControls(state: WindowState) {
    val position = when (val p = state.position) {
        WindowPosition.PlatformDefault -> "default"
        WindowPosition.Center -> "center"
        is WindowPosition.Absolute -> "${p.x.toInt()},${p.y.toInt()}"
    }
    TextBlock("window=${state.width.toInt()}x${state.height.toInt()} ${state.placement} at $position", Modifier.testTag("windowEcho"))
    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button({ state.width += 100.0 }, Modifier.testTag("wider")) { TextBlock("Wider") }
        Button({ state.position = WindowPosition.Absolute(100.0, 80.0) }, Modifier.testTag("moveTo")) { TextBlock("Move to 100,80") }
        Button({ state.placement = WindowPlacement.Maximized }, Modifier.testTag("maximize")) { TextBlock("Maximize") }
        Button({ state.placement = WindowPlacement.Floating }, Modifier.testTag("restore")) { TextBlock("Restore") }
    }
}

@Composable
private fun Inputs() {
    var name by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var wifi by remember { mutableStateOf(true) }
    var volume by remember { mutableDoubleStateOf(40.0) }
    var size by remember { mutableIntStateOf(1) }
    val sizes = remember { listOf("Small", "Medium", "Large") }

    TextBox(name, { name = it }, Modifier.widthIn(max = 320.0).testTag("name"), placeholder = "Name")
    TextBlock("Hello, ${name.ifEmpty { "nobody" }}", Modifier.testTag("greeting"))

    // The application rejects anything but digits; the box keeps showing the accepted text.
    TextBox(digits, { if (it.all { c -> c in '0'..'9' }) digits = it }, Modifier.widthIn(max = 320.0).testTag("digits"), placeholder = "Digits only")
    TextBlock("digits=$digits", Modifier.testTag("digitsEcho"))

    CheckBox(agreed, { agreed = it }, Modifier.testTag("agree")) { TextBlock("I agree") }
    TextBlock("agreed=${yesNo(agreed)}", Modifier.testTag("agreeEcho"))

    ToggleSwitch(wifi, { wifi = it }, Modifier.testTag("wifi"), header = "Wi-Fi")
    TextBlock("wifi=${yesNo(wifi)}", Modifier.testTag("wifiEcho"))

    Slider(volume, { volume = it }, Modifier.width(240.0).testTag("volume"))
    ProgressBar(volume, Modifier.width(240.0).testTag("progress"))
    TextBlock("volume=${volume.toInt()}", Modifier.testTag("volumeEcho"))

    ComboBox(sizes, size, { size = it }, Modifier.testTag("size"))
    TextBlock("size=${sizes.getOrElse(size) { "none" }}", Modifier.testTag("sizeEcho"))
}

@Composable
private fun MoreControls() {
    val options = remember { listOf("A", "B", "C") }
    var choice by remember { mutableStateOf("A") }
    var links by remember { mutableIntStateOf(0) }
    var quantity by remember { mutableDoubleStateOf(1.0) }
    var secret by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf(false) }

    StackPanel(orientation = Orientation.Horizontal, spacing = 12.0) {
        for (option in options) {
            RadioButton(option == choice, { choice = option }, Modifier.testTag("radio$option")) { TextBlock("Option $option") }
        }
    }
    TextBlock("choice=$choice", Modifier.testTag("choiceEcho"))

    HyperlinkButton({ links++ }, Modifier.testTag("link")) { TextBlock("A hyperlink button") }
    TextBlock("links=$links", Modifier.testTag("linkEcho"))

    NumberBox(quantity, { quantity = it }, Modifier.width(160.0).testTag("quantity"), minimum = 1.0, maximum = 99.0)
    TextBlock("quantity=${quantity.toInt()}", Modifier.testTag("quantityEcho"))

    PasswordBox(secret, { secret = it }, Modifier.widthIn(max = 320.0).testTag("password"), placeholder = "Password")
    TextBlock("passwordLength=${secret.length}", Modifier.testTag("passwordEcho"))

    ProgressRing(Modifier.testTag("ring"))

    Button({ notice = true }, Modifier.testTag("showNotice")) { TextBlock("Show notice") }
    InfoBar(notice, "Saved", "All changes are saved.", Modifier.testTag("notice"), severity = Severity.Success, onDismiss = { notice = false })
    TextBlock("notice=${yesNo(notice)}", Modifier.testTag("noticeEcho"))

    Dialog()
    Menus()
}

@Composable
private fun Menus() {
    var action by remember { mutableStateOf("none") }
    var wrap by remember { mutableStateOf(false) }
    val menu = listOf(
        MenuEntry.Item("Copy", Icon.Symbol(Symbol.Copy)) { action = "copy" },
        MenuEntry.Toggle("Word wrap", wrap) { wrap = it },
        MenuEntry.Separator,
        MenuEntry.SubMenu("Export", listOf(MenuEntry.Item("PNG") { action = "export:png" }, MenuEntry.Item("SVG") { action = "export:svg" })),
    )
    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
        DropDownButton(menu, Modifier.testTag("dropDown")) { TextBlock("Actions") }
        Button({}, Modifier.testTag("contextTarget").contextMenu(listOf(MenuEntry.Item("Context action") { action = "context" }))) {
            TextBlock("Right-click me")
        }
    }
    TextBlock("menu=$action wrap=${yesNo(wrap)}", Modifier.testTag("menuEcho"))
}

@Composable
private fun Dialog() {
    var asking by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf("none") }
    var reason by remember { mutableStateOf("") }

    Button({ asking = true; reason = "" }, Modifier.testTag("ask")) { TextBlock("Delete item…") }
    TextBlock("dialog=$outcome", Modifier.testTag("dialogEcho"))
    if (asking) {
        ContentDialog(
            onDismissRequest = { outcome = "cancelled"; asking = false },
            title = "Delete the item?",
            primaryButtonText = "Delete",
            onPrimaryClick = { outcome = "deleted:$reason"; asking = false },
            secondaryButtonText = "Archive",
            onSecondaryClick = { outcome = "archived"; asking = false },
            closeButtonText = "Cancel",
            defaultButton = DialogButton.Primary,
        ) {
            // State inside the dialog composes like any other content.
            StackPanel(spacing = 8.0) {
                TextBlock("This cannot be undone.")
                TextBox(reason, { reason = it }, Modifier.testTag("reason"), placeholder = "Reason")
            }
        }
    }
}

private class Item(val id: Int)

@Composable
private fun KeyedList() {
    var items by remember { mutableStateOf(listOf<Item>()) }
    var next by remember { mutableIntStateOf(1) }

    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button({ items = items + Item(next); next++ }, Modifier.testTag("add")) { TextBlock("Add") }
        Button({ items = items.drop(1) }, Modifier.testTag("removeFirst"), enabled = items.isNotEmpty()) { TextBlock("Remove first") }
        Button({ items = items.reversed() }, Modifier.testTag("reverse")) { TextBlock("Reverse") }
    }
    StackPanel(Modifier.testTag("list"), spacing = 4.0) {
        for (item in items) {
            // The check box state is remembered per key, so it follows its item when the list is reordered.
            key(item.id) {
                var done by remember { mutableStateOf(false) }
                CheckBox(done, { done = it }, Modifier.testTag("done${item.id}")) { TextBlock("Item ${item.id}") }
            }
        }
    }
    TextBlock("count=${items.size}", Modifier.testTag("count"))
}

@Composable
private fun LazyList() {
    var prefix by remember { mutableStateOf("Row") }
    var count by remember { mutableIntStateOf(10_000) }
    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button({ prefix = if (prefix == "Row") "Item" else "Row" }, Modifier.testTag("renameRows")) { TextBlock("Rename rows") }
        Button({ count-- }, Modifier.testTag("removeRow")) { TextBlock("Remove last row") }
    }
    // Only the rows in view exist as elements; scrolling realizes and recycles them.
    LazyItems((0 until count).toList(), Modifier.height(200.0).testTag("lazy"), spacing = 2.0) { i ->
        TextBlock("$prefix $i", Modifier.testTag("row$i"))
    }
}

private class Person(val id: Int)

@Composable
private fun KeyedLazyList() {
    var people by remember { mutableStateOf((1..6).map { Person(it) }) }
    var next by remember { mutableIntStateOf(7) }
    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button({ people = people.reversed() }, Modifier.testTag("reversePeople")) { TextBlock("Reverse") }
        Button({ people = listOf(Person(next)) + people; next++ }, Modifier.testTag("insertPerson")) { TextBlock("Insert at top") }
        Button({ people = people.drop(1) }, Modifier.testTag("removePerson")) { TextBlock("Remove first") }
    }
    // Keyed by id: the star remembered inside each realized row follows its person through reorders.
    LazyItems(people, Modifier.height(240.0).testTag("keyedLazy"), key = { it.id }) { person ->
        var starred by remember { mutableStateOf(false) }
        CheckBox(starred, { starred = it }, Modifier.testTag("star${person.id}")) { TextBlock("Person ${person.id}") }
    }
    TextBlock("people=${people.take(3).joinToString(",") { it.id.toString() }}", Modifier.testTag("peopleEcho"))
}

@Composable
private fun Layout() {
    val outline = remember { SolidColorBrush(Colors.Gray) }
    Grid(
        Modifier.testTag("grid"),
        rows = listOf(GridSize.Auto, GridSize.Auto),
        columns = listOf(GridSize.Pixels(80.0), GridSize.Star()),
        rowSpacing = 4.0,
        columnSpacing = 8.0,
    ) {
        TextBlock("Row 0", Modifier.cell(0, 0))
        TextBlock("Grid cell 0,1", Modifier.cell(0, 1))
        TextBlock("Row 1", Modifier.cell(1, 0))
        Border(Modifier.cell(1, 1).padding(8.0), borderBrush = outline, borderThickness = 1.0, cornerRadius = 4.0) {
            TextBlock("In a border", Modifier.toolTip("A tooltip").semantics("bordered text"))
        }
    }
    // Padding on a TextBlock (no padding in its style), a control (padding from its style) and a panel.
    var padded by remember { mutableStateOf(false) }
    CheckBox(padded, { padded = it }, Modifier.testTag("padToggle")) { TextBlock("Padding 24") }
    val pad = if (padded) Modifier.padding(24.0) else Modifier
    TextBlock("Padded text", pad.testTag("padText"))
    Button(onClick = {}, modifier = pad.testTag("padButton")) { TextBlock("Padded button") }
    StackPanel(pad) { TextBlock("In a padded panel", Modifier.testTag("padPanelChild")) }
    // A Canvas has no padding: the modifier has no effect and prints a warning once.
    Canvas(pad.size(40.0, 8.0)) {}
}
