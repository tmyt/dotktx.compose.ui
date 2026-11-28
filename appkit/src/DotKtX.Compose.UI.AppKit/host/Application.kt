package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import AppKit.NSApplication
import AppKit.NSApplicationActivationPolicy
import AppKit.NSEvent
import AppKit.NSEventModifierMask
import AppKit.NSEventType
import AppKit.NSMenu
import AppKit.NSMenuItem
import CoreFoundation.DispatchQueue
import CoreGraphics.CGPoint
import Foundation.NSProcessInfo
import Foundation.NSThread
import System.IntPtr

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
    if (!NSThread.IsMain) {
        throw IllegalStateException("application { } must be called on the main thread")
    }
    NSApplication.Init()
    val app = NSApplication.SharedApplication
    app.ActivationPolicy = NSApplicationActivationPolicy.Regular
    val runtime = UIRuntime.forMainThread()
    val host = ApplicationHost(runtime)
    installDefaultMainMenu(host)
    host.start(content)
    app.ActivateIgnoringOtherApps(true)
    app.Run()
}

/** Installs a main menu that holds only the application menu. */
internal fun installDefaultMainMenu(host: ApplicationHost) {
    val menu = NSMenu()
    menu.AddItem(defaultAppMenu(host))
    NSApplication.SharedApplication.MainMenu = menu
}

/** The first item of the main menu: the application menu with "Quit <process name>" (Cmd+Q). */
internal fun defaultAppMenu(host: ApplicationHost): NSMenuItem {
    val name = NSProcessInfo.ProcessInfo.ProcessName
    val quit = NSMenuItem("Quit $name", "q") { _, _ -> host.exitApplication() }
    quit.KeyEquivalentModifierMask = NSEventModifierMask.CommandKeyMask
    val appMenu = NSMenu()
    appMenu.AddItem(quit)
    val item = NSMenuItem()
    item.Submenu = appMenu
    return item
}

/**
 * Owns the application-level composition, on the main thread's [UIRuntime]. Windows are composed as
 * effects of this composition and count themselves in and out through [windowOpened] and
 * [windowClosed].
 */
internal class ApplicationHost(private val runtime: UIRuntime) : ApplicationScope {
    private var composition: Composition? = null
    private var windowCount = 0
    private var exiting = false

    fun start(content: @Composable ApplicationScope.() -> Unit) {
        val root = Composition(ApplicationApplier(), runtime.recomposer)
        composition = root
        root.setContent { CompositionLocalProvider(LocalDispatchQueue provides DispatchQueue.MainQueue) { content() } }
    }

    internal fun windowOpened() {
        windowCount++
    }

    internal fun windowClosed() {
        windowCount--
        if (windowCount == 0) exitApplication()
    }

    override fun exitApplication() {
        if (exiting) return
        exiting = true
        // Deferred to its own queue turn: this may be called while the composition applies changes,
        // and a composition cannot be disposed from inside its own apply phase.
        DispatchQueue.MainQueue.DispatchAsync {
            composition?.dispose()
            composition = null
            runtime.shutdown()
            val app = NSApplication.SharedApplication
            app.Stop(app)
            // Stop only takes effect once an event has been processed, so post one.
            val wakeUp = NSEvent.OtherEvent(NSEventType.ApplicationDefined, CGPoint(0.0, 0.0), NO_MODIFIERS, 0.0, IntPtr.Zero, null, 0, IntPtr.Zero, IntPtr.Zero)
            app.PostEvent(wakeUp, true)
        }
    }
}

/** The empty modifier mask (the binding has no `None` member). */
private val NO_MODIFIERS = NSEventModifierMask.ShiftKeyMask xor NSEventModifierMask.ShiftKeyMask
