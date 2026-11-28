package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import CoreFoundation.DispatchQueue

/** The main queue, for code that schedules work outside coroutines. */
val LocalDispatchQueue = staticCompositionLocalOf<DispatchQueue> { error("No DispatchQueue: LocalDispatchQueue is only available inside application { }") }

/** Receiver of [application]'s content. */
interface ApplicationScope {
    /** Closes every window and ends the application. */
    fun exitApplication()
}

/**
 * Runs an AppKit application whose windows are declared by [content]. Returns when the application
 * exits: after [ApplicationScope.exitApplication], or when [content] no longer contains a window.
 *
 * AppKit runs on the main thread, so call this from `main`.
 *
 * ```
 * fun main() = application {
 *     Window(onCloseRequest = ::exitApplication, title = "Hello") { Label("Hello") }
 * }
 * ```
 */
fun application(content: @Composable ApplicationScope.() -> Unit) {
    TODO("U1")
}

/**
 * Owns the application-level composition, on the main thread's [UIRuntime]. Windows are composed as
 * effects of this composition and count themselves in and out through [windowOpened] and
 * [windowClosed].
 */
internal class ApplicationHost(private val runtime: UIRuntime) : ApplicationScope {
    fun start(content: @Composable ApplicationScope.() -> Unit) {
        TODO("U1")
    }

    internal fun windowOpened() {
        TODO("U1")
    }

    internal fun windowClosed() {
        TODO("U1")
    }

    override fun exitApplication() {
        TODO("U1")
    }
}
