import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dotktx.compose.ui.winui.Button
import dotktx.compose.ui.winui.CheckBox
import dotktx.compose.ui.winui.CommandBar
import dotktx.compose.ui.winui.CommandBarEntry
import dotktx.compose.ui.winui.Flyout
import dotktx.compose.ui.winui.Grid
import dotktx.compose.ui.winui.GridSize
import dotktx.compose.ui.winui.MenuBar
import dotktx.compose.ui.winui.MenuBarMenu
import dotktx.compose.ui.winui.MenuEntry
import dotktx.compose.ui.winui.Modifier
import dotktx.compose.ui.winui.ScrollViewer
import dotktx.compose.ui.winui.StackPanel
import dotktx.compose.ui.winui.TeachingTip
import dotktx.compose.ui.winui.TextBlock
import dotktx.compose.ui.winui.TextBox
import dotktx.compose.ui.winui.WebView2
import dotktx.compose.ui.winui.Window
import dotktx.compose.ui.winui.application
import dotktx.compose.ui.winui.gridCell
import dotktx.compose.ui.winui.height
import dotktx.compose.ui.winui.margin
import dotktx.compose.ui.winui.popupAnchor
import dotktx.compose.ui.winui.rememberPopupAnchor
import dotktx.compose.ui.winui.rememberWindowState
import dotktx.compose.ui.winui.testTag
import Microsoft.UI.Xaml.Controls.CommandBarDefaultLabelPosition
import Microsoft.UI.Xaml.Controls.Orientation
import Microsoft.UI.Xaml.Controls.Symbol
import Microsoft.UI.Xaml.Controls.Primitives.FlyoutPlacementMode

fun main() {
    // WebView2 keeps its browser profile next to the executable by default. The sample can run from a
    // network path (\\wsl.localhost\… when started from WSL), so the profile goes to a local folder.
    System.Environment.SetEnvironmentVariable(
        "WEBVIEW2_USER_DATA_FOLDER",
        System.IO.Path.Combine(System.IO.Path.GetTempPath(), "DotKtX.Compose.UI.Commands.WebView2"),
    )
    application {
        Window(onCloseRequest = ::exitApplication, title = "Commands", state = rememberWindowState(width = 960.0, height = 900.0)) {
            Grid(rows = listOf(GridSize.Auto, GridSize.Auto, GridSize.Star())) {
                Menus(Modifier.gridCell(0, 0))
                Commands(Modifier.gridCell(1, 0))
                ScrollViewer(Modifier.gridCell(2, 0)) {
                    StackPanel(Modifier.margin(16.0), spacing = 16.0) {
                        Popups()
                        Browser()
                    }
                }
            }
        }
    }
}

@Composable
private fun Menus(modifier: Modifier) {
    var last by remember { mutableStateOf("none") }
    var statusBar by remember { mutableStateOf(false) }
    StackPanel(modifier) {
        MenuBar(
            listOf(
                MenuBarMenu("File", listOf(
                    MenuEntry.Item("New", Symbol.Add) { last = "new" },
                    MenuEntry.Separator,
                    MenuEntry.SubMenu("Export", listOf(MenuEntry.Item("PDF") { last = "export:pdf" })),
                )),
                MenuBarMenu("View", listOf(
                    MenuEntry.Toggle("Status bar", checked = statusBar) { statusBar = it },
                    // Never accepted: the item must keep showing the composed value.
                    MenuEntry.Toggle("Read only", checked = false) { last = "readonly:rejected" },
                )),
            ),
            Modifier.testTag("menuBar"),
        )
        TextBlock("menu=$last statusBar=${yesNo(statusBar)}", Modifier.margin(12.0, 0.0).testTag("menuEcho"))
    }
}

@Composable
private fun Commands(modifier: Modifier) {
    var last by remember { mutableStateOf("none") }
    var bold by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(false) }
    var barOpen by remember { mutableStateOf(false) }
    var rejected by remember { mutableIntStateOf(0) }
    StackPanel(modifier) {
        CommandBar(
            listOf(
                CommandBarEntry.Button("Save", Symbol.Save) { last = "save" },
                CommandBarEntry.Separator,
                CommandBarEntry.Toggle("Bold", Symbol.Bold, checked = bold) { bold = it },
                // Never accepted: the button must return to the composed value.
                CommandBarEntry.Toggle("Pin", Symbol.Pin, checked = false) { rejected++ },
            ),
            Modifier.testTag("commandBar"),
            secondaryCommands = listOf(
                CommandBarEntry.Button("Settings", Symbol.Setting) { last = "settings" },
                CommandBarEntry.Toggle("Wrap", checked = wrap) { wrap = it },
            ),
            isOpen = barOpen,
            onOpenChange = { barOpen = it },
            defaultLabelPosition = CommandBarDefaultLabelPosition.Right,
        ) {
            TextBlock("Document.txt", Modifier.margin(12.0, 14.0).testTag("barContent"))
        }
        StackPanel(Modifier.margin(12.0, 0.0), orientation = Orientation.Horizontal, spacing = 8.0) {
            TextBlock(
                "command=$last bold=${yesNo(bold)} wrap=${yesNo(wrap)} rejected=$rejected open=${yesNo(barOpen)}",
                Modifier.testTag("commandEcho"),
            )
            Button({ barOpen = true }, Modifier.testTag("openBar")) { TextBlock("Open overflow") }
            Button({ barOpen = false }, Modifier.testTag("closeBar")) { TextBlock("Close overflow") }
            Button({ bold = !bold }, Modifier.testTag("toggleBold")) { TextBlock("Toggle bold") }
        }
    }
}

@Composable
private fun Popups() {
    val renameAnchor = rememberPopupAnchor()
    var renaming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("draft") }
    var flyoutDismissals by remember { mutableIntStateOf(0) }

    val tipAnchor = rememberPopupAnchor()
    var showTip by remember { mutableStateOf(false) }
    var keepTip by remember { mutableStateOf(false) }
    var tipActions by remember { mutableIntStateOf(0) }
    var tipDismissals by remember { mutableIntStateOf(0) }

    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button({ renaming = true }, Modifier.popupAnchor(renameAnchor).testTag("rename")) { TextBlock("Rename") }
        Button({ showTip = true }, Modifier.popupAnchor(tipAnchor).testTag("showTip")) { TextBlock("Show tip") }
        Button({ showTip = false }, Modifier.testTag("hideTip")) { TextBlock("Hide tip") }
        CheckBox(keepTip, { keepTip = it }, Modifier.testTag("keepTip")) { TextBlock("Keep tip open") }
    }
    TextBlock("flyout=${if (renaming) "open" else "closed"} name=$name dismissals=$flyoutDismissals", Modifier.testTag("flyoutEcho"))
    TextBlock("tip=${if (showTip) "open" else "closed"} actions=$tipActions dismissals=$tipDismissals", Modifier.testTag("tipEcho"))

    if (renaming) {
        Flyout(renameAnchor, onDismissRequest = { flyoutDismissals++; renaming = false }, placement = FlyoutPlacementMode.Bottom) {
            StackPanel(spacing = 8.0) {
                TextBlock("Rename the document", Modifier.testTag("flyoutText"))
                TextBox(name, { name = it }, Modifier.testTag("flyoutName"))
                Button({ renaming = false }, Modifier.testTag("flyoutDone")) { TextBlock("Done") }
            }
        }
    }

    TeachingTip(
        isOpen = showTip,
        onDismissRequest = { tipDismissals++; if (!keepTip) showTip = false },
        title = "Tip title",
        subtitle = "Tip subtitle",
        modifier = Modifier.testTag("tip"),
        target = tipAnchor,
        actionButtonText = "Do it",
        onActionClick = { tipActions++ },
        closeButtonText = "Got it",
    ) {
        TextBlock("Tip content", Modifier.testTag("tipContent"))
    }
}

private fun page(title: String, body: String): String =
    "data:text/html," + System.Uri.EscapeDataString("<html><head><title>$title</title></head><body><h1>$title</h1>$body</body></html>")

private val pageA = page("Page A", "<p>First page</p>")
private val pageB = page("Page B", "<p>Second page</p>")

// Navigates by itself a second after it loads, like a page whose script sets location.
private val pageC = page("Page C", "<script>setTimeout(function () { location.href = 'about:blank'; }, 1000);</script>")

@Composable
private fun Browser() {
    var source by remember { mutableStateOf(pageA) }
    var locked by remember { mutableStateOf(false) }
    var rejected by remember { mutableIntStateOf(0) }
    var navigated by remember { mutableStateOf("none") }
    var navigations by remember { mutableIntStateOf(0) }
    val label = when (source) {
        pageA -> "A"
        pageB -> "B"
        pageC -> "C"
        else -> source
    }
    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button({ source = pageA }, Modifier.testTag("pageA")) { TextBlock("Page A") }
        Button({ source = pageB }, Modifier.testTag("pageB")) { TextBlock("Page B") }
        Button({ source = pageC }, Modifier.testTag("pageC")) { TextBlock("Page C (navigates away)") }
        CheckBox(locked, { locked = it }, Modifier.testTag("lockSource")) { TextBlock("Lock source") }
    }
    TextBlock("source=$label rejected=$rejected", Modifier.testTag("sourceEcho"))
    TextBlock("navigated=$navigated count=$navigations", Modifier.testTag("navigatedEcho"))
    WebView2(
        source,
        onSourceChange = { if (locked) rejected++ else source = it },
        modifier = Modifier.height(240.0).testTag("web"),
        onNavigated = { navigated = "${it.title}:${if (it.success) "ok" else "failed"}"; navigations++ },
    )
}

private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
