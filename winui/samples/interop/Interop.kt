import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dotktx.compose.ui.winui.Button
import dotktx.compose.ui.winui.ComposeHost
import dotktx.compose.ui.winui.ContentDialog
import dotktx.compose.ui.winui.Modifier
import dotktx.compose.ui.winui.StackPanel
import dotktx.compose.ui.winui.TextBlock
import dotktx.compose.ui.winui.bootstrapWindowsAppSDK
import dotktx.compose.ui.winui.shutdownWindowsAppSDK
import dotktx.compose.ui.winui.testTag
import Microsoft.UI.Xaml.Application
import Microsoft.UI.Xaml.LaunchActivatedEventArgs
import Microsoft.UI.Xaml.Thickness
import Microsoft.UI.Xaml.Automation.AutomationProperties
import Microsoft.UI.Xaml.Controls.XamlControlsResources
import Microsoft.UI.Xaml.Markup.IXamlMetadataProvider
import Microsoft.UI.Xaml.Markup.IXamlType
import Microsoft.UI.Xaml.Markup.XmlnsDefinition
import Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider
import System.Threading.ApartmentState
import System.Threading.Thread
import System.Threading.ThreadStart
import System.Type
import Microsoft.UI.Xaml.Controls.Button as XamlButton
import Microsoft.UI.Xaml.Controls.StackPanel as XamlStackPanel
import Microsoft.UI.Xaml.Controls.TextBlock as XamlTextBlock
import Microsoft.UI.Xaml.Window as XamlWindow

// An ordinary WinUI app, written without `application { }`, that hosts one Compose screen.

/** Snapshot state written by plain WinUI event handlers and read by the Compose content. */
private val nativeClicks = mutableIntStateOf(0)

fun main() {
    val ui = Thread(ThreadStart {
        bootstrapWindowsAppSDK()
        Application.Start { _ -> InteropApp() }
        shutdownWindowsAppSDK()
    })
    ui.SetApartmentState(ApartmentState.STA)
    ui.Start()
    ui.Join()
}

private class InteropApp : Application(), IXamlMetadataProvider {
    private val xamlMetadata = XamlControlsXamlMetaDataProvider()

    override fun GetXamlType(type: Type): IXamlType? = xamlMetadata.GetXamlType(type)
    override fun GetXamlType(fullName: String): IXamlType? = xamlMetadata.GetXamlType(fullName)
    override fun GetXmlnsDefinitions(): Array<XmlnsDefinition> = xamlMetadata.GetXmlnsDefinitions()

    override fun OnLaunched(args: LaunchActivatedEventArgs) {
        Resources.MergedDictionaries.Add(XamlControlsResources())

        val label = XamlTextBlock()
        label.Text = "Native clicks: 0"
        AutomationProperties.SetAutomationId(label, "nativeLabel")

        val button = XamlButton()
        button.Content = "Native +1"
        AutomationProperties.SetAutomationId(button, "nativeButton")
        button.Click.subscribe { _, _ ->
            nativeClicks.intValue++
            label.Text = "Native clicks: " + nativeClicks.intValue
        }

        val host = ComposeHost()
        host.setContent { ComposeScreen() }

        val panel = XamlStackPanel()
        panel.Spacing = 12.0
        panel.Margin = Thickness(24.0, 24.0, 24.0, 24.0)
        panel.Children.Add(label)
        panel.Children.Add(button)
        panel.Children.Add(host)

        val window = XamlWindow()
        window.Title = "Interop"
        window.Content = panel
        window.Activate()
    }
}

@Composable
private fun ComposeScreen() {
    var count by remember { mutableIntStateOf(0) }
    var asking by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("none") }
    StackPanel(spacing = 8.0) {
        TextBlock("Compose clicks: $count, native clicks: ${nativeClicks.intValue}", Modifier.testTag("composeLabel"))
        Button({ count++ }, Modifier.testTag("composeButton")) { TextBlock("Compose +1") }
        Button({ asking = true }, Modifier.testTag("composeDialog")) { TextBlock("Ask") }
        TextBlock("dialog=$answer", Modifier.testTag("dialogEcho"))
    }
    if (asking) {
        ContentDialog(
            onDismissRequest = { answer = "dismissed"; asking = false },
            title = "Hosted dialog",
            primaryButtonText = "OK",
            onPrimaryClick = { answer = "ok"; asking = false },
            closeButtonText = "Cancel",
        ) { TextBlock("Shown from a ComposeHost.") }
    }
}
