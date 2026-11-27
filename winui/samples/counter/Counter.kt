import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dotktx.compose.ui.winui.Alignment
import dotktx.compose.ui.winui.Button
import dotktx.compose.ui.winui.Modifier
import dotktx.compose.ui.winui.TextBlock
import dotktx.compose.ui.winui.Window
import dotktx.compose.ui.winui.align
import dotktx.compose.ui.winui.application

// Left without @STAThreadAttribute on purpose: the counter (also the package smoke test) covers the
// path where application { } starts its own STA thread, and the MAIN_WITHOUT_STA_THREAD warning that
// the analyzers report for it.
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Counter") {
        var count by remember { mutableIntStateOf(0) }
        Button(onClick = { count++ }, modifier = Modifier.align(Alignment.Center)) {
            TextBlock("$count")
        }
    }
}
