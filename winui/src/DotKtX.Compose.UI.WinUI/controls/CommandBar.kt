package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentRecomposeScope
import Microsoft.UI.Xaml.Controls.AppBarButton
import Microsoft.UI.Xaml.Controls.AppBarSeparator
import Microsoft.UI.Xaml.Controls.AppBarToggleButton
import Microsoft.UI.Xaml.Controls.CommandBarDefaultLabelPosition
import Microsoft.UI.Xaml.Controls.ICommandBarElement
import Microsoft.UI.Xaml.Controls.Symbol
import Microsoft.UI.Xaml.Controls.SymbolIcon
import System.Collections.Generic.IList
import Microsoft.UI.Xaml.Controls.CommandBar as XamlCommandBar

/** A command of a [CommandBar]. */
sealed class CommandBarEntry {
    /** A command button (`AppBarButton`). */
    data class Button(
        val label: String,
        val icon: Symbol? = null,
        val enabled: Boolean = true,
        val onClick: () -> Unit,
    ) : CommandBarEntry()

    /** An on/off button (`AppBarToggleButton`), controlled by [checked]. */
    data class Toggle(
        val label: String,
        val icon: Symbol? = null,
        val checked: Boolean,
        val enabled: Boolean = true,
        val onCheckedChange: (Boolean) -> Unit,
    ) : CommandBarEntry()

    /** A dividing line (`AppBarSeparator`). */
    object Separator : CommandBarEntry()
}

/**
 * A toolbar of commands (`CommandBar`). [primaryCommands] are shown as buttons on the bar;
 * [secondaryCommands] are in the overflow menu that the "More" button opens.
 *
 * Toggle commands are controlled: toggling one calls its `onCheckedChange`, and the button shows the
 * composed `checked` value again if the application does not accept the change. The bar keeps its
 * buttons while the commands keep their kinds and order, and recreates them otherwise.
 *
 * The overflow menu is left to WinUI while [isOpen] is null. Otherwise it is controlled by [isOpen],
 * and opening or closing it by hand (including the automatic close after a secondary command)
 * calls [onOpenChange]. [content] is shown at the start of the bar (`CommandBar.Content`) and must
 * emit at most one element.
 *
 * ```
 * CommandBar(
 *     listOf(
 *         CommandBarEntry.Button("Save", Symbol.Save) { save() },
 *         CommandBarEntry.Separator,
 *         CommandBarEntry.Toggle("Bold", Symbol.Bold, checked = bold) { bold = it },
 *     ),
 *     secondaryCommands = listOf(CommandBarEntry.Button("Settings", Symbol.Setting) { showSettings = true }),
 * ) { TextBlock(fileName) }
 * ```
 */
@Composable
fun CommandBar(
    primaryCommands: List<CommandBarEntry>,
    modifier: Modifier = Modifier,
    secondaryCommands: List<CommandBarEntry> = emptyList(),
    isOpen: Boolean? = null,
    onOpenChange: (Boolean) -> Unit = {},
    defaultLabelPosition: CommandBarDefaultLabelPosition = CommandBarDefaultLabelPosition.Bottom,
    content: @Composable () -> Unit = {},
) {
    val scope = currentRecomposeScope
    Container({ CommandBarNode(XamlCommandBar()) }, modifier, {
        set(scope) { this.scope = it; open.scope = it }
        set(onOpenChange) { this.onOpenChange = it }
        set(defaultLabelPosition) { element.DefaultLabelPosition = it }
        reconcile {
            primary.update(primaryCommands, this.scope)
            secondary.update(secondaryCommands, this.scope)
            openControlled = isOpen != null
            if (isOpen != null) open.sync(isOpen)
        }
    }, content)
}

internal class CommandBarNode(bar: XamlCommandBar) :
    ElementNode<XamlCommandBar>(bar, ChildSlot.Single("CommandBar") { bar.Content = it }) {
    var scope: RecomposeScope? = null
    var onOpenChange: (Boolean) -> Unit = {}
    var openControlled = false
    val primary = CommandList(bar.PrimaryCommands)
    val secondary = CommandList(bar.SecondaryCommands)

    val open = ControlledValue(read = { bar.IsOpen }, write = { bar.IsOpen = it })

    init {
        // Opening and Closing are raised synchronously with the IsOpen change (Opened and Closed
        // follow after the animation).
        onRelease(bar.Opening.subscribe { _, _ -> if (openControlled) open.userChanged(onOpenChange) })
        onRelease(bar.Closing.subscribe { _, _ -> if (openControlled) open.userChanged(onOpenChange) })
        onRelease(AutoCloseable {
            primary.release()
            secondary.release()
        })
    }
}

/**
 * The commands of one `CommandBar` collection (`PrimaryCommands` or `SecondaryCommands`). Buttons are
 * updated in place while the entries keep their kinds and order; any other change recreates them.
 */
internal class CommandList(private val items: IList<ICommandBarElement>) {
    private val commands = ArrayList<BarCommand>()

    fun update(entries: List<CommandBarEntry>, scope: RecomposeScope?) {
        if (!sameKinds(entries)) {
            release()
            items.Clear()
            for (entry in entries) {
                val command = BarCommand.create(entry)
                commands.add(command)
                items.Add(command.element)
            }
        }
        for (i in entries.indices) commands[i].update(entries[i], scope)
    }

    private fun sameKinds(entries: List<CommandBarEntry>): Boolean {
        if (entries.size != commands.size) return false
        for (i in entries.indices) if (!commands[i].accepts(entries[i])) return false
        return true
    }

    /** Closes the event subscriptions of the current buttons. */
    fun release() {
        for (command in commands) command.release()
        commands.clear()
    }
}

/** One element of a [CommandList], with its subscriptions and the latest callback. */
internal abstract class BarCommand {
    abstract val element: ICommandBarElement
    private val subscriptions = ArrayList<AutoCloseable>()

    abstract fun accepts(entry: CommandBarEntry): Boolean

    abstract fun update(entry: CommandBarEntry, scope: RecomposeScope?)

    protected fun subscription(action: AutoCloseable) {
        subscriptions.add(action)
    }

    fun release() {
        for (action in subscriptions) action.close()
        subscriptions.clear()
    }

    companion object {
        fun create(entry: CommandBarEntry): BarCommand = when (entry) {
            is CommandBarEntry.Button -> ButtonCommand()
            is CommandBarEntry.Toggle -> ToggleCommand()
            CommandBarEntry.Separator -> SeparatorCommand()
        }
    }
}

private class ButtonCommand : BarCommand() {
    private val button = AppBarButton()
    override val element: ICommandBarElement get() = button
    private var onClick: () -> Unit = {}
    private var last: CommandBarEntry.Button? = null

    init {
        subscription(button.Click.subscribe { _, _ -> onClick() })
    }

    override fun accepts(entry: CommandBarEntry): Boolean = entry is CommandBarEntry.Button

    override fun update(entry: CommandBarEntry, scope: RecomposeScope?) {
        val e = entry as CommandBarEntry.Button
        onClick = e.onClick
        val previous = last
        if (previous == null || previous.label != e.label) button.Label = e.label
        if (previous == null || previous.icon != e.icon) {
            if (e.icon == null) button.ClearValue(AppBarButton.IconProperty) else button.Icon = SymbolIcon(e.icon)
        }
        if (previous == null || previous.enabled != e.enabled) button.IsEnabled = e.enabled
        last = e
    }
}

private class ToggleCommand : BarCommand() {
    private val button = AppBarToggleButton()
    override val element: ICommandBarElement get() = button
    private var onCheckedChange: (Boolean) -> Unit = {}
    private var last: CommandBarEntry.Toggle? = null
    private val input = ControlledValue(read = { button.IsChecked == true }, write = { button.IsChecked = it })

    init {
        subscription(button.Checked.subscribe { _, _ -> input.userChanged(onCheckedChange) })
        subscription(button.Unchecked.subscribe { _, _ -> input.userChanged(onCheckedChange) })
    }

    override fun accepts(entry: CommandBarEntry): Boolean = entry is CommandBarEntry.Toggle

    override fun update(entry: CommandBarEntry, scope: RecomposeScope?) {
        val e = entry as CommandBarEntry.Toggle
        onCheckedChange = e.onCheckedChange
        input.scope = scope
        val previous = last
        if (previous == null || previous.label != e.label) button.Label = e.label
        if (previous == null || previous.icon != e.icon) {
            if (e.icon == null) button.ClearValue(AppBarToggleButton.IconProperty) else button.Icon = SymbolIcon(e.icon)
        }
        if (previous == null || previous.enabled != e.enabled) button.IsEnabled = e.enabled
        input.sync(e.checked)
        last = e
    }
}

private class SeparatorCommand : BarCommand() {
    override val element: ICommandBarElement = AppBarSeparator()

    override fun accepts(entry: CommandBarEntry): Boolean = entry === CommandBarEntry.Separator

    override fun update(entry: CommandBarEntry, scope: RecomposeScope?) {}
}
