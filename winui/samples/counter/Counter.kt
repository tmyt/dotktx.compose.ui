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

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Counter") {
        var count by remember { mutableIntStateOf(0) }
        Button(onClick = { count++ }, modifier = Modifier.align(Alignment.Center)) {
            TextBlock("$count")
        }
    }
}
