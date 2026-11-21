package dotktx.compose.ui.winui

import androidx.compose.runtime.RecomposeScope

/**
 * Keeps a WinUI control's own copy of a value (`TextBox.Text`, `ToggleSwitch.IsOn`, …) in line with
 * the value from the composition, which is the single source of truth.
 *
 * - [sync] runs on every recomposition of the wrapper and writes the composed value only when the
 *   control holds something else, so an accepted edit is not written back (no echo, caret and IME
 *   composition stay intact).
 * - [userChanged] runs from the control's change event. It reports the control's value to the
 *   application and invalidates the wrapper's recompose scope, so the next recomposition calls
 *   [sync] with whatever the application decided. If the application rejected the edit, nothing
 *   else recomposes and [sync] restores the composed value.
 * - Events raised while the node itself writes to the control are ignored.
 */
internal class ControlledValue<T>(
    private val read: () -> T,
    private val write: (T) -> Unit,
) {
    /** Recompose scope of the wrapper composable. */
    var scope: RecomposeScope? = null

    /** While an IME composition is active the control's text is provisional and is not overwritten. */
    var composing: Boolean = false
        set(value) {
            field = value
            if (!value) scope?.invalidate()
        }

    private var hasComposed = false
    private var composed: T? = null
    private var writing = false

    fun sync(value: T) {
        composed = value
        hasComposed = true
        if (!composing && read() != value) programmatic { write(value) }
    }

    fun userChanged(callback: (T) -> Unit) {
        if (writing || !hasComposed) return
        val value = read()
        if (value == composed) return
        callback(value)
        scope?.invalidate()
    }

    /** Runs [block], which changes the control from code, without reporting the change as user input. */
    fun programmatic(block: () -> Unit) {
        val wasWriting = writing
        writing = true
        try {
            block()
        } finally {
            writing = wasWriting
        }
    }
}
