import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dotktx.compose.ui.appkit.Alignment
import dotktx.compose.ui.appkit.AppKitView
import dotktx.compose.ui.appkit.Box
import dotktx.compose.ui.appkit.Button
import dotktx.compose.ui.appkit.CheckBox
import dotktx.compose.ui.appkit.Label
import dotktx.compose.ui.appkit.MenuBar
import dotktx.compose.ui.appkit.MenuBarMenu
import dotktx.compose.ui.appkit.MenuEntry
import dotktx.compose.ui.appkit.Modifier
import dotktx.compose.ui.appkit.Orientation
import dotktx.compose.ui.appkit.PopUpButton
import dotktx.compose.ui.appkit.ProgressIndicator
import dotktx.compose.ui.appkit.RadioButton
import dotktx.compose.ui.appkit.ScrollView
import dotktx.compose.ui.appkit.SecureTextField
import dotktx.compose.ui.appkit.Slider
import dotktx.compose.ui.appkit.StackView
import dotktx.compose.ui.appkit.Switch
import dotktx.compose.ui.appkit.TextField
import dotktx.compose.ui.appkit.Window
import dotktx.compose.ui.appkit.WindowPlacement
import dotktx.compose.ui.appkit.WindowPosition
import dotktx.compose.ui.appkit.WindowState
import dotktx.compose.ui.appkit.align
import dotktx.compose.ui.appkit.alpha
import dotktx.compose.ui.appkit.application
import dotktx.compose.ui.appkit.contextMenu
import dotktx.compose.ui.appkit.height
import dotktx.compose.ui.appkit.heightIn
import dotktx.compose.ui.appkit.hidden
import dotktx.compose.ui.appkit.margin
import dotktx.compose.ui.appkit.padding
import dotktx.compose.ui.appkit.rememberWindowState
import dotktx.compose.ui.appkit.semantics
import dotktx.compose.ui.appkit.testTag
import dotktx.compose.ui.appkit.toolTip
import dotktx.compose.ui.appkit.width
import dotktx.compose.ui.appkit.widthIn
import AppKit.NSColor
import AppKit.NSLevelIndicator

fun main() = application {
    var showSettings by remember { mutableStateOf(false) }
    var statusBar by remember { mutableStateOf(true) }
    var lastCommand by remember { mutableStateOf("none") }
    val mainWindow = rememberWindowState(width = 720.0, height = 640.0, position = WindowPosition.Center)

    MenuBar(
        listOf(
            MenuBarMenu(
                "File",
                listOf(
                    MenuEntry.Item("New note") { lastCommand = "new" },
                    MenuEntry.Toggle("Status bar", statusBar) { statusBar = it },
                    MenuEntry.Separator,
                    MenuEntry.SubMenu(
                        "Export",
                        listOf(
                            MenuEntry.Item("As PNG") { lastCommand = "export:png" },
                            MenuEntry.Item("As PDF") { lastCommand = "export:pdf" },
                        ),
                    ),
                ),
            ),
        ),
    )

    Window(onCloseRequest = ::exitApplication, title = "Gallery", state = mainWindow) {
        ScrollView(Modifier.testTag("galleryScroll")) {
            StackView(Modifier.padding(24.0), spacing = 12.0) {
                Label("Window", fontSize = 20.0)
                WindowControls(mainWindow)
                Label("Menus", fontSize = 20.0)
                Label("menu=$lastCommand statusBar=${yesNo(statusBar)}", Modifier.testTag("menuEcho"))
                Label("Inputs", fontSize = 20.0)
                Inputs()
                Label("More controls", fontSize = 20.0)
                MoreControls()
                Label("Keyed list", fontSize = 20.0)
                KeyedList()
                Label("Layout", fontSize = 20.0)
                Layout()
                Label("Context menu", fontSize = 20.0)
                ContextMenu()
                Label("AppKit view", fontSize = 20.0)
                NativeView()
                Button("Open settings", onClick = { showSettings = true }, modifier = Modifier.testTag("openSettings"))
            }
        }
    }
    if (showSettings) {
        Window(onCloseRequest = { showSettings = false }, title = "Settings") {
            Label("Settings window", Modifier.align(Alignment.Center).testTag("settingsText"))
        }
    }
}

// An if chain: a `when (val p = …)` over WindowPosition made DotKt 0.9.34 emit invalid IL in a Release build.
private fun describe(position: WindowPosition): String {
    if (position is WindowPosition.Absolute) return "${position.x.toInt()},${position.y.toInt()}"
    if (position == WindowPosition.Center) return "center"
    return "default"
}

private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

@Composable
private fun WindowControls(state: WindowState) {
    val position = describe(state.position)
    Label("window=${state.width.toInt()}x${state.height.toInt()} ${state.placement} at $position", Modifier.testTag("windowEcho"))
    StackView(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button("Wider", { state.width += 100.0 }, Modifier.testTag("wider"))
        Button("Move to 100,80", { state.position = WindowPosition.Absolute(100.0, 80.0) }, Modifier.testTag("moveTo"))
        Button("Maximize", { state.placement = WindowPlacement.Maximized }, Modifier.testTag("maximize"))
        Button("Restore", { state.placement = WindowPlacement.Floating }, Modifier.testTag("restore"))
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

    TextField(name, { name = it }, Modifier.width(240.0).testTag("name"), placeholder = "Name")
    Label("Hello, ${name.ifEmpty { "nobody" }}", Modifier.testTag("greeting"))

    // The application rejects anything but digits; the field keeps showing the accepted text.
    TextField(digits, { if (it.all { c -> c in '0'..'9' }) digits = it }, Modifier.width(240.0).testTag("digits"), placeholder = "Digits only")
    Label("digits=$digits", Modifier.testTag("digitsEcho"))

    CheckBox("I agree", agreed, { agreed = it }, Modifier.testTag("agree"))
    Label("agreed=${yesNo(agreed)}", Modifier.testTag("agreeEcho"))

    Switch(wifi, { wifi = it }, Modifier.testTag("wifi"))
    Label("wifi=${yesNo(wifi)}", Modifier.testTag("wifiEcho"))

    Slider(volume, { volume = it }, Modifier.width(240.0).testTag("volume"))
    ProgressIndicator(volume, Modifier.width(240.0).testTag("progress"))
    Label("volume=${volume.toInt()}", Modifier.testTag("volumeEcho"))

    PopUpButton(sizes, size, { size = it }, Modifier.testTag("size"))
    Label("size=${sizes.getOrElse(size) { "none" }}", Modifier.testTag("sizeEcho"))
}

@Composable
private fun MoreControls() {
    val options = remember { listOf("A", "B", "C") }
    var choice by remember { mutableStateOf("A") }
    var secret by remember { mutableStateOf("") }

    StackView(orientation = Orientation.Horizontal, spacing = 12.0) {
        for (option in options) {
            RadioButton("Option $option", option == choice, { choice = option }, Modifier.testTag("radio$option"))
        }
    }
    Label("choice=$choice", Modifier.testTag("choiceEcho"))

    SecureTextField(secret, { secret = it }, Modifier.width(240.0).testTag("password"), placeholder = "Password")
    Label("passwordLength=${secret.length}", Modifier.testTag("passwordEcho"))
}

private class Item(val id: Int)

@Composable
private fun KeyedList() {
    var items by remember { mutableStateOf(listOf<Item>()) }
    var next by remember { mutableIntStateOf(1) }

    StackView(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button("Add", { items = items + Item(next); next++ }, Modifier.testTag("add"))
        Button("Reverse", { items = items.reversed() }, Modifier.testTag("reverse"))
    }
    StackView(Modifier.testTag("list"), spacing = 4.0) {
        for (item in items) {
            // The check box state is remembered per key, so it follows its item when the list is reordered.
            key(item.id) {
                var done by remember { mutableStateOf(false) }
                StackView(orientation = Orientation.Horizontal, spacing = 8.0, alignment = Alignment.CenterStart) {
                    CheckBox("Item ${item.id}", done, { done = it }, Modifier.testTag("done${item.id}"))
                    Button("Move up", {
                        val index = items.indexOfFirst { it.id == item.id }
                        if (index > 0) {
                            val reordered = items.toMutableList()
                            reordered.add(index - 1, reordered.removeAt(index))
                            items = reordered
                        }
                    }, Modifier.testTag("up${item.id}"))
                    Button("Remove", { items = items.filter { it.id != item.id } }, Modifier.testTag("remove${item.id}"))
                }
            }
        }
    }
    Label("count=${items.size}", Modifier.testTag("count"))
}

@Composable
private fun Layout() {
    var shown by remember { mutableStateOf(true) }
    var padded by remember { mutableStateOf(false) }
    val pad = if (padded) Modifier.padding(24.0) else Modifier

    // Cross-axis alignment of a horizontal stack, and a child that overrides it.
    val variants = remember {
        listOf("TopStart" to Alignment.TopStart, "CenterStart" to Alignment.CenterStart, "BottomStart" to Alignment.BottomStart)
    }
    for (variant in variants) {
        StackView(Modifier.height(48.0), orientation = Orientation.Horizontal, spacing = 8.0, alignment = variant.second) {
            Label(variant.first)
            Button("Button", {})
            Label("override", Modifier.align(Alignment.TopStart))
        }
    }

    Box(
        Modifier.testTag("box").padding(8.0).width(280.0),
        background = NSColor.SystemYellow,
        borderColor = NSColor.Gray,
        borderWidth = 1.0,
        cornerRadius = 6.0,
    ) {
        Label("In a box", Modifier.toolTip("A tooltip").semantics("boxed text"))
    }

    CheckBox("Show the next label", shown, { shown = it }, Modifier.testTag("hiddenToggle"))
    Label("Hidden labels take no space", Modifier.hidden(!shown).testTag("hiddenLabel"))

    // widthIn / heightIn bound the size; the box stretches up to its maximum.
    Box(
        Modifier.widthIn(min = 120.0, max = 260.0).heightIn(min = 32.0, max = 64.0).testTag("bounded"),
        background = NSColor.SystemTeal,
        cornerRadius = 4.0,
    ) {
        Label("widthIn 120..260, heightIn 32..64", Modifier.align(Alignment.Center))
    }

    CheckBox("Padding 24", padded, { padded = it }, Modifier.testTag("padToggle"))
    StackView(pad.testTag("padStack")) {
        Label("In a padded stack", Modifier.margin(4.0).alpha(0.8).testTag("padChild"))
    }
}

@Composable
private fun ContextMenu() {
    var action by remember { mutableStateOf("none") }
    var wrap by remember { mutableStateOf(false) }
    val menu = listOf(
        MenuEntry.Item("Copy") { action = "copy" },
        MenuEntry.Toggle("Word wrap", wrap) { wrap = it },
        MenuEntry.Separator,
        MenuEntry.SubMenu(
            "Export",
            listOf(MenuEntry.Item("PNG") { action = "export:png" }, MenuEntry.Item("SVG") { action = "export:svg" }),
        ),
    )
    Label("Right-click me", Modifier.contextMenu(menu).testTag("contextTarget"))
    Label("context=$action wrap=${yesNo(wrap)}", Modifier.testTag("contextEcho"))
}

@Composable
private fun NativeView() {
    var level by remember { mutableDoubleStateOf(3.0) }
    // Any AppKit view can be placed with AppKitView; update runs after each recomposition of this function.
    AppKitView(
        factory = {
            val indicator = NSLevelIndicator()
            indicator.MinValue = 0.0
            indicator.MaxValue = 10.0
            indicator
        },
        modifier = Modifier.width(200.0).testTag("levelIndicator"),
        update = { it.DoubleValue = level },
    )
    Slider(level, { level = it }, Modifier.width(200.0).testTag("level"), min = 0.0, max = 10.0)
    Label("level=${level.toInt()}", Modifier.testTag("levelEcho"))
}
