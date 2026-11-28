// In-process smoke test of DotKtX.Compose.UI.AppKit: composes the library's composables in a window,
// drives them through AppKit (clicks, text-change notifications, state writes) and checks what the
// composition and the view tree show. The first failed check prints "FAIL: …" and exits with 1; a
// run that reaches the end prints "PASS" and exits with 0. Run with scripts/smoke-appkit.sh.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import dotktx.compose.ui.appkit.Alignment
import dotktx.compose.ui.appkit.ApplicationScope
import dotktx.compose.ui.appkit.Box
import dotktx.compose.ui.appkit.Button
import dotktx.compose.ui.appkit.CheckBox
import dotktx.compose.ui.appkit.Label
import dotktx.compose.ui.appkit.MenuBar
import dotktx.compose.ui.appkit.MenuBarMenu
import dotktx.compose.ui.appkit.MenuEntry
import dotktx.compose.ui.appkit.Modifier
import dotktx.compose.ui.appkit.Orientation
import dotktx.compose.ui.appkit.PopUpButton
import dotktx.compose.ui.appkit.ScrollView
import dotktx.compose.ui.appkit.Slider
import dotktx.compose.ui.appkit.StackView
import dotktx.compose.ui.appkit.Switch
import dotktx.compose.ui.appkit.TextField
import dotktx.compose.ui.appkit.Window
import dotktx.compose.ui.appkit.WindowPosition
import dotktx.compose.ui.appkit.WindowState
import dotktx.compose.ui.appkit.align
import dotktx.compose.ui.appkit.application
import dotktx.compose.ui.appkit.contextMenu
import dotktx.compose.ui.appkit.hidden
import dotktx.compose.ui.appkit.padding
import dotktx.compose.ui.appkit.rememberWindowState
import dotktx.compose.ui.appkit.size
import dotktx.compose.ui.appkit.testTag
import kotlin.math.abs
import AppKit.NSApplication
import AppKit.NSButton
import AppKit.NSCellStateValue
import AppKit.NSColor
import AppKit.NSControl
import AppKit.NSPopUpButton
import AppKit.NSSlider
import AppKit.NSTextField
import AppKit.NSView
import AppKit.NSWindow
import Foundation.NSNotificationCenter
import System.Environment
import System.IntPtr

/** State shared between the composition and the scenario. */
private object S {
    var count by mutableIntStateOf(0)
    var name by mutableStateOf("")
    var digits by mutableStateOf("")
    var agreed by mutableStateOf(false)
    var wifi by mutableStateOf(true)
    var volume by mutableDoubleStateOf(40.0)
    var size by mutableIntStateOf(1)
    var hide by mutableStateOf(false)
    var items by mutableStateOf(listOf(1, 2, 3))
    var showSecond by mutableStateOf(false)
    var flag by mutableStateOf(false)
    var contextClicks by mutableIntStateOf(0)
}

fun main() {
    application {
        val state = rememberWindowState(width = 500.0, height = 400.0, position = WindowPosition.Center)
        MenuBar(
            listOf(
                MenuBarMenu("Edit", listOf(MenuEntry.Toggle("Flag", S.flag) { S.flag = it }, MenuEntry.Separator, MenuEntry.Item("Noop") {})),
            ),
        )
        Window(onCloseRequest = ::exitApplication, title = "Smoke", state = state) {
            Content()
            LaunchedEffect(Unit) {
                try {
                    scenario(state)
                } catch (e: Throwable) {
                    fail("scenario threw: $e")
                }
            }
        }
        if (S.showSecond) {
            Window(onCloseRequest = { S.showSecond = false }, title = "Second") {
                Label("second", Modifier.testTag("secondLabel"))
            }
        }
    }
    println("PASS")
}

@Composable
private fun Content() {
    ScrollView(Modifier.testTag("scroll")) {
        StackView(Modifier.padding(16.0).testTag("stack"), spacing = 8.0) {
            Label("count=${S.count}", Modifier.testTag("countLabel"))
            Button("Increment", { S.count++ }, Modifier.testTag("inc"))
            TextField(S.name, { S.name = it }, Modifier.testTag("name"), placeholder = "Name")
            Label("hello ${S.name}", Modifier.testTag("greeting"))
            // Rejects anything but digits; the field keeps showing the accepted text.
            TextField(S.digits, { if (it.all { c -> c in '0'..'9' }) S.digits = it }, Modifier.testTag("digits"))
            CheckBox("Agree", S.agreed, { S.agreed = it }, Modifier.testTag("agree"))
            Switch(S.wifi, { S.wifi = it }, Modifier.testTag("wifi"))
            Slider(S.volume, { S.volume = it }, Modifier.testTag("volume"))
            PopUpButton(listOf("S", "M", "L"), S.size, { S.size = it }, Modifier.testTag("size"))
            StackView(Modifier.testTag("row"), orientation = Orientation.Horizontal, spacing = 4.0) {
                Label("A", Modifier.testTag("a"))
                Label("B", Modifier.testTag("b"))
            }
            Box(Modifier.size(200.0, 60.0).testTag("box"), background = NSColor.Red) {
                Label("boxed", Modifier.align(Alignment.Center).testTag("boxed"))
            }
            Label("maybe hidden", Modifier.hidden(S.hide).testTag("maybe"))
            for (item in S.items) key(item) { Label("item $item", Modifier.testTag("item$item")) }
            Label("context", Modifier.testTag("context").contextMenu(listOf(MenuEntry.Item("Hit") { S.contextClicks++ })))
            Button("Open second", { S.showSecond = true }, Modifier.testTag("openSecond"))
        }
    }
}

private suspend fun ApplicationScope.scenario(state: WindowState) {
    val app = NSApplication.SharedApplication
    settle()
    val window = windowTitled("Smoke")
    val root = window.ContentView!!

    // Layout: the stack fills the window, children follow each other vertically.
    expect(abs(window.ContentRectFor(window.Frame).Width.Value - 500.0) <= 1.0, "window content width is 500")
    val stack = find(root, "stack")
    val countLabel = find(root, "countLabel")
    val inc = find(root, "inc") as NSButton
    expect(stack.Frame.Width.Value >= 490.0, "stack stretches to the window width (got ${stack.Frame.Width.Value})")
    expect(countLabel.Frame.Y.Value >= 16.0, "padding puts the first child below the top (y=${countLabel.Frame.Y.Value})")
    expect(inc.Frame.Y.Value > countLabel.Frame.Y.Value + countLabel.Frame.Height.Value, "children are stacked vertically")
    expect(inc.Frame.Height.Value > 10.0, "button has its fitting height")
    expect(text(countLabel) == "count=0", "initial count label")

    // Clicks reach the latest lambda and recompose only the label.
    repeat(3) { inc.PerformClick(inc) }
    settle()
    expect(S.count == 3, "three clicks counted")
    expect(text(countLabel) == "count=3", "count label after clicks")

    // Controlled text: accepted edit shows, rejected edit is undone.
    val name = find(root, "name") as NSTextField
    type(name, "Ann")
    settle()
    expect(S.name == "Ann", "name state updated")
    expect(text(find(root, "greeting")) == "hello Ann", "greeting recomposed")
    val digits = find(root, "digits") as NSTextField
    type(digits, "12a")
    settle()
    expect(S.digits == "", "rejected edit not stored")
    expect(digits.StringValue == "", "rejected edit undone in the field (got '${digits.StringValue}')")
    type(digits, "12")
    settle()
    expect(S.digits == "12" && digits.StringValue == "12", "accepted digits")

    // Toggles and ranges.
    val agree = find(root, "agree") as NSButton
    agree.PerformClick(agree)
    settle()
    expect(S.agreed, "check box reported")
    expect(agree.State == NSCellStateValue.On, "check box stays on")
    val wifi = find(root, "wifi") as NSControl
    wifi.PerformClick(wifi)
    settle()
    expect(!S.wifi, "switch reported off")
    val volume = find(root, "volume") as NSSlider
    volume.DoubleValue = 70.0
    volume.SendAction(volume.Action!!, volume.Target!!)
    settle()
    expect(abs(S.volume - 70.0) < 0.001, "slider reported 70 (got ${S.volume})")
    val size = find(root, "size") as NSPopUpButton
    size.SelectItem(IntPtr(2))
    size.SendAction(size.Action!!, size.Target!!)
    settle()
    expect(S.size == 2, "pop-up reported index 2 (got ${S.size})")
    // A value the application rejects is restored from the composition.
    S.size = 0
    settle()
    expect(size.IndexOfSelectedItem.ToInt32() == 0, "pop-up follows the composed index")

    // Horizontal stack and Box alignment.
    val a = find(root, "a")
    val b = find(root, "b")
    expect(b.Frame.X.Value > a.Frame.X.Value + a.Frame.Width.Value, "horizontal stack places B after A")
    expect(abs(a.Frame.Y.Value - b.Frame.Y.Value) < 0.5, "horizontal stack aligns A and B at the top")
    val box = find(root, "box")
    val boxed = find(root, "boxed")
    expect(abs(box.Frame.Width.Value - 200.0) < 0.5 && abs(box.Frame.Height.Value - 60.0) < 0.5, "box has its fixed size")
    expect(abs(boxed.Frame.X.Value - (200.0 - boxed.Frame.Width.Value) / 2) < 1.0, "boxed label centered horizontally")
    expect(abs(boxed.Frame.Y.Value - (60.0 - boxed.Frame.Height.Value) / 2) < 1.0, "boxed label centered vertically")

    // Hidden children take no space.
    val maybe = find(root, "maybe")
    val openSecond = find(root, "openSecond")
    val before = openSecond.Frame.Y.Value
    S.hide = true
    settle()
    expect(maybe.Hidden, "label hidden")
    expect(openSecond.Frame.Y.Value < before, "following child moved up when a child was hidden")
    S.hide = false
    settle()
    expect(abs(openSecond.Frame.Y.Value - before) < 0.5, "following child moved back")

    // Keyed list: removal and reorder keep the remaining views.
    val item2 = find(root, "item2")
    val item3 = find(root, "item3")
    val item3Y = item3.Frame.Y.Value
    S.items = listOf(1, 3)
    settle()
    expect(findOrNull(root, "item2") == null, "removed item left the tree")
    expect(item2.Superview == null, "removed view detached")
    expect(find(root, "item3") === item3, "kept item keeps its view")
    expect(item3.Frame.Y.Value < item3Y, "kept item moved up")
    S.items = listOf(3, 1)
    settle()
    expect(find(root, "item3").Frame.Y.Value < find(root, "item1").Frame.Y.Value, "reordered items")
    expect(find(root, "item1").Superview === stack, "moved item still in the stack")

    // Scroll view: the document is at least the viewport.
    val scroll = find(root, "scroll") as AppKit.NSScrollView
    val document = scroll.DocumentView as NSView
    expect(document.Frame.Height.Value >= scroll.ContentView.Bounds.Height.Value - 0.5, "document covers the viewport")
    expect(document.Frame.Height.Value >= openSecond.Frame.Y.Value + openSecond.Frame.Height.Value, "document covers the content")

    // Window state in both directions.
    state.width = 600.0
    settle()
    expect(abs(window.ContentRectFor(window.Frame).Width.Value - 600.0) <= 1.0, "window resized from state")
    expect(stack.Frame.Width.Value >= 590.0, "stack follows the window width (got ${stack.Frame.Width.Value})")
    state.position = WindowPosition.Absolute(100.0, 80.0)
    settle()
    expect(abs(window.Frame.X.Value - 100.0) <= 1.0, "window moved from state (x=${window.Frame.X.Value})")
    window.SetContentSize(CoreGraphics.CGSize(520.0, 400.0))
    settle()
    expect(abs(state.width - 520.0) <= 1.0, "state follows a window resize (got ${state.width})")

    // Menus.
    val main = app.MainMenu!!
    expect(main.Count.ToInt32() == 2, "main menu has the app menu and Edit (got ${main.Count.ToInt32()})")
    val edit = main.ItemAt(IntPtr(1))!!.Submenu!!
    // AppKit appends its own items (dictation, emoji) to a menu titled Edit; ours come first.
    expect(edit.Count.ToInt32() >= 3 && edit.ItemAt(IntPtr(0))!!.Title == "Flag" && edit.ItemAt(IntPtr(1))!!.IsSeparatorItem && edit.ItemAt(IntPtr(2))!!.Title == "Noop", "Edit menu starts with the composed items")
    val flagItem = edit.ItemAt(IntPtr(0))!!
    expect(flagItem.State == NSCellStateValue.Off, "toggle starts off")
    S.flag = true
    settle()
    expect(app.MainMenu!!.ItemAt(IntPtr(1))!!.Submenu!!.ItemAt(IntPtr(0))!!.State == NSCellStateValue.On, "toggle rebuilt as on")
    val context = find(root, "context")
    val contextMenu = context.Menu!!
    expect(contextMenu.Count.ToInt32() == 1, "context menu built")
    contextMenu.PerformActionForItem(IntPtr(0))
    settle()
    expect(S.contextClicks == 1, "context menu item invoked")

    // A second window opens and closes through state.
    S.showSecond = true
    settle()
    val second = windowTitled("Second")
    expect(second.IsVisible, "second window visible")
    expect(findOrNull(second.ContentView!!, "secondLabel") != null, "second window composed")
    S.showSecond = false
    settle()
    expect(windowTitledOrNull("Second") == null || !windowTitledOrNull("Second")!!.IsVisible, "second window closed")

    exitApplication()
}

/** Waits two frames (recomposition and apply) and lays out every window's content. */
private suspend fun settle() {
    withFrameNanos { }
    withFrameNanos { }
    layoutAllWindows()
}

// Kept out of the suspend function: DotKt cannot lower a suspend function whose body holds a for loop.
private fun layoutAllWindows() {
    for (window in NSApplication.SharedApplication.DangerousWindows) window.ContentView?.LayoutSubtreeIfNeeded()
}

/** Types [text] into a field the way AppKit reports an edit: the value changes, then the control posts its notification. */
private fun type(field: NSTextField, text: String) {
    field.StringValue = text
    NSNotificationCenter.DefaultCenter.PostNotificationName(NSControl.TextDidChangeNotification.ToString(), field)
}

private fun text(view: NSView): String = (view as NSTextField).StringValue

private fun windowTitledOrNull(title: String): NSWindow? {
    for (window in NSApplication.SharedApplication.DangerousWindows) if (window.Title == title) return window
    return null
}

private fun windowTitled(title: String): NSWindow {
    val window = windowTitledOrNull(title)
    if (window == null) fail("no window titled $title")
    return window!!
}

private fun findOrNull(view: NSView, tag: String): NSView? {
    if (view.AccessibilityIdentifier == tag) return view
    for (sub in view.Subviews) {
        val found = findOrNull(sub, tag)
        if (found != null) return found
    }
    return null
}

private fun find(view: NSView, tag: String): NSView {
    val found = findOrNull(view, tag)
    if (found == null) fail("no view tagged $tag")
    return found!!
}

private fun expect(condition: Boolean, what: String) {
    if (condition) println("ok: $what") else fail(what)
}

private fun fail(what: String) {
    println("FAIL: $what")
    Environment.Exit(1)
}
