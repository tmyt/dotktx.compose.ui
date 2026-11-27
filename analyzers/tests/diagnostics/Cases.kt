// A line that must get a diagnostic ends with an expect comment naming it. scripts/test-analyzers.sh
// compares these with what the compiler reports, so every other line must stay free of analyzer
// diagnostics.
// Only errors are covered: the compiler does not print warnings for sources that have errors.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import dotktx.compose.ui.winui.Border
import dotktx.compose.ui.winui.Button
import dotktx.compose.ui.winui.ComposeHost
import dotktx.compose.ui.winui.SplitView
import dotktx.compose.ui.winui.StackPanel
import dotktx.compose.ui.winui.TextBlock
import dotktx.compose.ui.winui.Window
import dotktx.compose.ui.winui.application
import System.STAThreadAttribute

@STAThreadAttribute
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Cases") {
        TextBlock("a")
        TextBlock("b") // expect: SEVERAL_ELEMENTS_IN_SINGLE_ELEMENT_CONTENT
        TextBlock("c")
    }
}

@Composable
fun Unmarked() {
    TextBlock("x")
}

@Composable
fun Violations(flag: Boolean) {
    Button(onClick = {}) {
        StackPanel { TextBlock("a") }
        Border { TextBlock("b") } // expect: SEVERAL_ELEMENTS_IN_SINGLE_ELEMENT_CONTENT
    }
    Button(content = {
        TextBlock("a")
        TextBlock("b") // expect: SEVERAL_ELEMENTS_IN_SINGLE_ELEMENT_CONTENT
    }, onClick = {})
    SplitView(
        paneOpen = true,
        onPaneOpenChange = {},
        pane = {
            TextBlock("a")
            TextBlock("b") // expect: SEVERAL_ELEMENTS_IN_SINGLE_ELEMENT_CONTENT
        },
    ) {
        TextBlock("a")
        if (flag) TextBlock("maybe")
        TextBlock("b") // expect: SEVERAL_ELEMENTS_IN_SINGLE_ELEMENT_CONTENT
    }
    Border {
        // A return inside a nested lambda does not leave this content.
        Button(onClick = { if (flag) return@Button }) { TextBlock("a") }
        TextBlock("b") // expect: SEVERAL_ELEMENTS_IN_SINGLE_ELEMENT_CONTENT
    }
}

fun hostViolation(host: ComposeHost) {
    host.setContent {
        TextBlock("a")
        TextBlock("b") // expect: SEVERAL_ELEMENTS_IN_SINGLE_ELEMENT_CONTENT
    }
}

@Composable
fun Accepted(flag: Boolean, items: List<String>) {
    Button(onClick = {}) { }
    Button(onClick = {}) { TextBlock("a") }
    // Separate conditions can be exclusive, and unmarked composables are not counted.
    Button(onClick = {}) {
        if (flag) TextBlock("a")
        if (!flag) TextBlock("b")
    }
    Button(onClick = {}) {
        if (flag) TextBlock("a") else TextBlock("b")
    }
    Button(onClick = {}) {
        Unmarked()
        Unmarked()
    }
    Button(onClick = {}) {
        TextBlock("a")
        Unmarked()
    }
    Button(onClick = {}) {
        for (item in items) TextBlock(item)
    }
    Button(onClick = {}) {
        key(flag) { TextBlock("a") }
        key(flag) { TextBlock("b") }
    }
    // The second element is not certain after a statement that can leave the content.
    Button(onClick = {}) {
        TextBlock("a")
        if (flag) return@Button
        TextBlock("b")
    }
    Button(onClick = {}) {
        TextBlock("a")
        items.firstOrNull() ?: return@Button
        TextBlock("b")
    }
    // Containers hold any number of elements.
    StackPanel {
        TextBlock("a")
        TextBlock("b")
    }
}
