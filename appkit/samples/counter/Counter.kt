import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dotktx.compose.ui.appkit.Alignment
import dotktx.compose.ui.appkit.Button
import dotktx.compose.ui.appkit.Modifier
import dotktx.compose.ui.appkit.Window
import dotktx.compose.ui.appkit.align
import dotktx.compose.ui.appkit.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Counter") {
        var count by remember { mutableIntStateOf(0) }
        Button("$count", onClick = { count++ }, modifier = Modifier.align(Alignment.Center))
    }
}
