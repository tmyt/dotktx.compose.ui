package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.PasswordBox as XamlPasswordBox

/**
 * Masked text input (`PasswordBox`), controlled by [password] in the same way as [TextBox].
 *
 * ```
 * PasswordBox(password = secret, onPasswordChange = { secret = it }, placeholder = "Password")
 * ```
 */
@Composable
fun PasswordBox(
    password: String,
    onPasswordChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    val scope = currentRecomposeScope
    Leaf({ PasswordBoxNode(XamlPasswordBox()) }, modifier) {
        set(scope) { input.scope = it }
        set(onPasswordChange) { this.onPasswordChange = it }
        set(enabled) { element.IsEnabled = it }
        set(placeholder) { element.PlaceholderText = it }
        reconcile { input.sync(password) }
    }
}

internal class PasswordBoxNode(box: XamlPasswordBox) : ElementNode<XamlPasswordBox>(box, ChildSlot.None("PasswordBox")) {
    var onPasswordChange: (String) -> Unit = {}

    val input = ControlledValue(read = { box.Password }, write = { box.Password = it })

    init {
        // PasswordChanging is raised synchronously with the edit; see TextBoxNode.
        onRelease(box.PasswordChanging.subscribe { _, _ -> input.userChanged(onPasswordChange) })
    }
}
