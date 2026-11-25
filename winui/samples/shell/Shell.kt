import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dotktx.compose.ui.winui.Icon
import dotktx.compose.ui.winui.Button
import dotktx.compose.ui.winui.Expander
import dotktx.compose.ui.winui.Grid
import dotktx.compose.ui.winui.GridSize
import dotktx.compose.ui.winui.Modifier
import dotktx.compose.ui.winui.NavigationItem
import dotktx.compose.ui.winui.NavigationView
import dotktx.compose.ui.winui.StackPanel
import dotktx.compose.ui.winui.TabItem
import dotktx.compose.ui.winui.TabView
import dotktx.compose.ui.winui.TextBlock
import dotktx.compose.ui.winui.TextBox
import dotktx.compose.ui.winui.TitleBar
import dotktx.compose.ui.winui.Window
import dotktx.compose.ui.winui.application
import dotktx.compose.ui.winui.margin
import dotktx.compose.ui.winui.rememberWindowState
import dotktx.compose.ui.winui.testTag
import Microsoft.UI.Xaml.Controls.NavigationViewPaneDisplayMode
import Microsoft.UI.Xaml.Controls.Symbol
import System.STAThreadAttribute

private val pages = listOf(
    NavigationItem("home", "Home", Icon.Symbol(Symbol.Home)),
    NavigationItem("documents", "Documents", Icon.Symbol(Symbol.Document)),
    NavigationItem("about", "About", Icon.Symbol(Symbol.Help)),
)

@STAThreadAttribute
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Shell", state = rememberWindowState(width = 960.0, height = 640.0)) {
        var page by remember { mutableStateOf("home") }
        var history by remember { mutableStateOf(listOf<String>()) }
        var paneOpen by remember { mutableStateOf(true) }

        Grid(rows = listOf(GridSize.Auto, GridSize.Star())) {
            TitleBar(
                "Shell",
                Modifier.cell(0, 0).testTag("titleBar"),
                subtitle = "Compose on WinUI",
                icon = Icon.Glyph("\uE80F"),
                onBack = if (history.isEmpty()) null else ({ page = history.last(); history = history.dropLast(1) }),
                onPaneToggle = { paneOpen = !paneOpen },
            )
            NavigationView(
                pages,
                selectedKey = page,
                onSelect = { if (it != page) { history = history + page; page = it } },
                modifier = Modifier.cell(1, 0).testTag("nav"),
                paneDisplayMode = NavigationViewPaneDisplayMode.Left,
                paneOpen = paneOpen,
                onPaneOpenChange = { paneOpen = it },
                paneToggleButton = false,
            ) {
                StackPanel(Modifier.margin(24.0), spacing = 12.0) {
                    TextBlock("page=$page history=${history.size} pane=${if (paneOpen) "open" else "closed"}", Modifier.testTag("shellEcho"))
                    when (page) {
                        "home" -> TextBlock("Welcome home", fontSize = 24.0)
                        "documents" -> Documents()
                        else -> About()
                    }
                }
            }
        }
    }
}

private class Doc(val id: Int, var text: String = "")

@Composable
private fun Documents() {
    var docs by remember { mutableStateOf(listOf(Doc(1), Doc(2))) }
    var next by remember { mutableIntStateOf(3) }
    var current by remember { mutableIntStateOf(0) }

    TabView(
        docs.map { TabItem("Doc ${it.id}") },
        selectedIndex = current,
        onSelect = { current = it },
        modifier = Modifier.testTag("tabs"),
        onClose = { index ->
            docs = docs.filterIndexed { i, _ -> i != index }
            current = current.coerceAtMost(docs.size - 1).coerceAtLeast(0)
        },
        onAdd = { docs = docs + Doc(next); next++; current = docs.size - 1 },
    ) {
        val doc = docs.getOrNull(current)
        if (doc != null) {
            StackPanel(Modifier.margin(12.0), spacing = 8.0) {
                TextBlock("Editing Doc ${doc.id}", Modifier.testTag("tabContent"))
                var text by remember(doc.id) { mutableStateOf(doc.text) }
                TextBox(text, { text = it; doc.text = it }, Modifier.testTag("docText"), placeholder = "Text of Doc ${doc.id}")
            }
        }
    }
    TextBlock("tabs=${docs.joinToString(",") { it.id.toString() }} selected=${docs.getOrNull(current)?.id ?: 0}", Modifier.testTag("tabsEcho"))
}

@Composable
private fun About() {
    var details by remember { mutableStateOf(false) }
    Expander("Details", expanded = details, onExpandedChange = { details = it }, modifier = Modifier.testTag("details")) {
        TextBlock("Built with the Compose runtime on WinUI 3.", Modifier.testTag("detailsText"))
    }
    TextBlock("expanded=${if (details) "yes" else "no"}", Modifier.testTag("detailsEcho"))
    Button({ details = !details }, Modifier.testTag("toggleDetails")) { TextBlock("Toggle details") }
}
