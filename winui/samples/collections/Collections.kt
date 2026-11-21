import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dotktx.compose.ui.winui.BreadcrumbBar
import dotktx.compose.ui.winui.Button
import dotktx.compose.ui.winui.CheckBox
import dotktx.compose.ui.winui.FlipView
import dotktx.compose.ui.winui.Grid
import dotktx.compose.ui.winui.GridSize
import dotktx.compose.ui.winui.GridView
import dotktx.compose.ui.winui.ListView
import dotktx.compose.ui.winui.Modifier
import dotktx.compose.ui.winui.ScrollViewer
import dotktx.compose.ui.winui.SelectorBar
import dotktx.compose.ui.winui.SelectorBarEntry
import dotktx.compose.ui.winui.StackPanel
import dotktx.compose.ui.winui.TextBlock
import dotktx.compose.ui.winui.TreeItem
import dotktx.compose.ui.winui.TreeView
import dotktx.compose.ui.winui.Window
import dotktx.compose.ui.winui.WindowPosition
import dotktx.compose.ui.winui.application
import dotktx.compose.ui.winui.gridCell
import dotktx.compose.ui.winui.height
import dotktx.compose.ui.winui.margin
import dotktx.compose.ui.winui.rememberWindowState
import dotktx.compose.ui.winui.size
import dotktx.compose.ui.winui.testTag
import Microsoft.UI.Xaml.Controls.Orientation
import Microsoft.UI.Xaml.Controls.Symbol

fun main() = application {
    val state = rememberWindowState(width = 1100.0, height = 900.0, position = WindowPosition.Center)
    Window(onCloseRequest = ::exitApplication, title = "Collections", state = state) {
        ScrollViewer {
            Grid(Modifier.margin(24.0), columns = listOf(GridSize.Star(), GridSize.Star()), columnSpacing = 24.0) {
                StackPanel(Modifier.gridCell(0, 0), spacing = 8.0) {
                    TextBlock("ListView", fontSize = 20.0)
                    Fruits()
                    TextBlock("ListView, multiple selection", fontSize = 20.0)
                    Letters()
                    TextBlock("GridView", fontSize = 20.0)
                    Colors()
                }
                StackPanel(Modifier.gridCell(0, 1), spacing = 8.0) {
                    TextBlock("TreeView", fontSize = 20.0)
                    Folders()
                    TextBlock("FlipView", fontSize = 20.0)
                    Pages()
                    TextBlock("BreadcrumbBar", fontSize = 20.0)
                    Breadcrumbs()
                    TextBlock("SelectorBar", fontSize = 20.0)
                    Selector()
                }
            }
        }
    }
}

private class Fruit(val id: Int, val name: String)

@Composable
private fun Fruits() {
    var fruits by remember { mutableStateOf(listOf("Apple", "Banana", "Cherry", "Damson").mapIndexed { i, name -> Fruit(i + 1, name) }) }
    var next by remember { mutableIntStateOf(5) }
    var selected by remember { mutableIntStateOf(-1) }
    var clicks by remember { mutableIntStateOf(0) }
    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button({ selected = 0 }, Modifier.testTag("selectFirstFruit")) { TextBlock("Select first") }
        Button({ fruits = listOf(Fruit(next, "Elderberry $next")) + fruits; next++ }, Modifier.testTag("insertFruit")) { TextBlock("Insert at top") }
        Button({ fruits = fruits.reversed() }, Modifier.testTag("reverseFruits")) { TextBlock("Reverse") }
    }
    // Keyed by id: the check box state remembered inside each row stays with its fruit when rows are inserted above it.
    ListView(
        fruits,
        selectedIndex = selected,
        onSelectedIndexChange = { selected = it },
        modifier = Modifier.height(220.0).testTag("fruits"),
        key = { it.id },
        onItemClick = { clicks++ },
    ) { fruit ->
        var ripe by remember { mutableStateOf(false) }
        StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
            CheckBox(ripe, { ripe = it }, Modifier.testTag("ripe${fruit.id}"))
            TextBlock(fruit.name, Modifier.testTag("fruit${fruit.id}"))
        }
    }
    TextBlock(
        "fruit=${fruits.getOrNull(selected)?.name ?: "none"} index=$selected order=${fruits.joinToString(",") { it.id.toString() }} clicks=$clicks",
        Modifier.testTag("fruitEcho"),
    )
}

@Composable
private fun Letters() {
    val letters = remember { listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo") }
    var selected by remember { mutableStateOf(setOf(1)) }
    Button({ selected = setOf(0, 4) }, Modifier.testTag("selectEnds")) { TextBlock("Select first and last") }
    ListView(letters, selectedIndices = selected, onSelectedIndicesChange = { selected = it }, modifier = Modifier.height(200.0).testTag("letters")) { letter ->
        TextBlock(letter, Modifier.testTag("letter$letter"))
    }
    TextBlock("letters=${selected.sorted().joinToString(",")}", Modifier.testTag("lettersEcho"))
}

@Composable
private fun Colors() {
    val colors = remember { listOf("Red", "Green", "Blue", "Cyan", "Magenta", "Yellow") }
    var selected by remember { mutableIntStateOf(-1) }
    Button({ selected = 5 }, Modifier.testTag("selectYellow")) { TextBlock("Select Yellow") }
    GridView(colors, selectedIndex = selected, onSelectedIndexChange = { selected = it }, modifier = Modifier.height(180.0).testTag("colors")) { color ->
        TextBlock(color, Modifier.size(90.0, 50.0).testTag("color$color"))
    }
    TextBlock("color=${colors.getOrNull(selected) ?: "none"}", Modifier.testTag("colorEcho"))
}

private val folders = listOf(
    TreeItem("Documents", listOf(
        TreeItem("Work", listOf(TreeItem("Report"), TreeItem("Slides"))),
        TreeItem("Personal"),
    )),
    TreeItem("Pictures", listOf(TreeItem("Holiday"))),
)

@Composable
private fun Folders() {
    var selected by remember { mutableStateOf<Any?>(null) }
    var expanded by remember { mutableStateOf(setOf<Any>("Documents")) }
    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button({ expanded = setOf("Documents", "Work", "Pictures") }, Modifier.testTag("expandAll")) { TextBlock("Expand all") }
        Button({ expanded = emptySet() }, Modifier.testTag("collapseAll")) { TextBlock("Collapse all") }
        Button({ selected = "Personal" }, Modifier.testTag("selectPersonal")) { TextBlock("Select Personal") }
    }
    TreeView(
        folders,
        selectedKey = selected,
        onSelectedKeyChange = { selected = it },
        modifier = Modifier.height(220.0).testTag("folders"),
        expandedKeys = expanded,
        onExpandedKeysChange = { expanded = it },
    ) { name ->
        TextBlock("[$name]", Modifier.testTag("folder$name"))
    }
    TextBlock("folder=${selected ?: "none"} expanded=${expanded.map { it.toString() }.sorted().joinToString(",")}", Modifier.testTag("folderEcho"))
}

@Composable
private fun Pages() {
    val pages = remember { (1..4).map { "Page $it" } }
    var page by remember { mutableIntStateOf(0) }
    Button({ page = pages.size - 1 }, Modifier.testTag("lastPage")) { TextBlock("Last page") }
    FlipView(pages, selectedIndex = page, onSelectedIndexChange = { page = it }, modifier = Modifier.size(320.0, 120.0).testTag("pages")) { title ->
        TextBlock(title, Modifier.testTag("page${title.removePrefix("Page ")}"), fontSize = 28.0)
    }
    TextBlock("page=$page", Modifier.testTag("pageEcho"))
}

@Composable
private fun Breadcrumbs() {
    var path by remember { mutableStateOf(listOf("Home", "Documents", "Work")) }
    Button({ path = path + "Folder ${path.size}" }, Modifier.testTag("deeper")) { TextBlock("Go deeper") }
    BreadcrumbBar(path, onItemClick = { path = path.take(it + 1) }, modifier = Modifier.testTag("crumbs"))
    TextBlock("path=${path.joinToString("/")}", Modifier.testTag("pathEcho"))
}

@Composable
private fun Selector() {
    val entries = remember {
        listOf(SelectorBarEntry("Recent", Symbol.Clock), SelectorBarEntry("Shared", Symbol.People), SelectorBarEntry("Favorites", Symbol.Favorite))
    }
    var view by remember { mutableIntStateOf(0) }
    Button({ view = 2 }, Modifier.testTag("selectFavorites")) { TextBlock("Show favorites") }
    SelectorBar(entries, selectedIndex = view, onSelectedIndexChange = { view = it }, modifier = Modifier.testTag("selector"))
    TextBlock("view=${entries.getOrNull(view)?.text ?: "none"}", Modifier.testTag("viewEcho"))
}
