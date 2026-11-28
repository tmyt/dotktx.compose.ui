package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import dotktx.compose.ui.annotations.EmitsOneElement
import AppKit.NSSecureTextField
import AppKit.NSTextField
import AppKit.NSTextView
import Foundation.NSRange
import System.IntPtr

/**
 * Single-line text input (`NSTextField`), controlled by [text].
 *
 * Every edit calls [onTextChange]. The field shows [text]: if the application does not update its
 * state with the new value, the edit is undone.
 *
 * ```
 * var name by remember { mutableStateOf("") }
 * TextField(text = name, onTextChange = { name = it })
 * ```
 */
@EmitsOneElement
@Composable
fun TextField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    TextFieldContent({ NSTextField() }, "TextField", text, onTextChange, modifier, enabled, placeholder)
}

/**
 * Password input (`NSSecureTextField`), controlled by [text]. Same behavior as [TextField] with the
 * characters masked.
 *
 * ```
 * SecureTextField(text = password, onTextChange = { password = it })
 * ```
 */
@EmitsOneElement
@Composable
fun SecureTextField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    TextFieldContent({ NSSecureTextField() }, "SecureTextField", text, onTextChange, modifier, enabled, placeholder)
}

@Composable
private fun TextFieldContent(
    create: () -> NSTextField,
    owner: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    placeholder: String,
) {
    val scope = currentRecomposeScope
    Leaf({ TextFieldNode(create(), owner) }, modifier) {
        set(scope) { input.scope = it }
        set(onTextChange) { this.onTextChange = it }
        set(enabled) { field.Enabled = it }
        set(placeholder) { field.PlaceholderString = it }
        reconcile { input.sync(text) }
    }
}

internal class TextFieldNode(val field: NSTextField, owner: String) : ElementNode<NSTextField>(field, ChildSlot.None(owner)) {
    var onTextChange: (String) -> Unit = {}

    val input = ControlledValue(read = { field.StringValue }, write = { value ->
        // Assigning StringValue ends editing and moves the caret; keep it where the user was typing.
        val editor = field.CurrentEditor
        val range = editor?.SelectedRange
        field.StringValue = value
        if (editor != null && range != null) editor.SelectedRange = NSRange(IntPtr(minOf(range.Location.ToInt32(), value.length)), IntPtr(0))
    })

    init {
        // Changed is raised synchronously with every edit and not by programmatic writes.
        onRelease(field.Changed.subscribe { _, _ ->
            val editor = field.CurrentEditor as? NSTextView
            input.composing = editor?.HasMarkedText == true
            if (!input.composing) input.userChanged(onTextChange)
        })
    }
}
