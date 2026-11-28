# DotKtX.Compose.UI.AppKit design

This document describes the design of `DotKtX.Compose.UI.AppKit`, a macOS AppKit binding for the Compose
runtime (`DotKtX.AndroidX.Compose.Runtime`, from `dotktx.compose`). It is the second backend of this
repository and has the same shape as the WinUI one ([design-winui.md](design-winui.md)): Compose decides
which native views exist, in what order and with which property values; AppKit keeps rendering, input,
text, IME, accessibility and theming. The composables are listed in the [README](../README.md).

Read the WinUI document first for what the two backends share (the applier, modifier chain, controlled
inputs, global snapshot writes). This one describes the AppKit code and where it differs.

## 1. Overview

The tree that Compose maintains is a tree of `AppKit.NSView`s, mutated through an `Applier`.

```kotlin
import androidx.compose.runtime.*
import dotktx.compose.ui.appkit.*

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Counter") {
        var count by remember { mutableIntStateOf(0) }
        Button("$count", onClick = { count++ }, modifier = Modifier.align(Alignment.Center))
    }
}
```

| Code | Mechanism |
|---|---|
| `application { }` | Main thread check, `NSApplication`, the main thread's runtime, `NSApplication.Run` (§3.1–§3.5) |
| `Window(…) { }` | A window as a composable; a child composition rooted at a `ContentHost` that is the `NSWindow`'s content view (§3.6) |
| `Button("$count", …)` | `ComposeNode` → `ElementNode<NSButton>`; the title is a property, not a child (§4, §6.1) |
| `Modifier.align(…)` | A layout property read by the enclosing `Panel`, diffed by key (§5) |
| `onClick = { count++ }` | One `Activated` subscription per node, routed to the latest lambda (§6.2) |
| `count++` | Global snapshot write → apply notification → recomposition → only `NSButton.Title` is set (§3.4, §6.1) |

```
application { … }                    application composition (ApplicationApplier, no nodes)
  └─ Window(…) { … }                 remember { NSWindow } + DisposableEffect; ContentView = ContentHost
       └─ window composition         AppKitApplier rooted at the ContentHost
            └─ Button, Label, StackView, …   ElementNode<V : NSView>
UIRuntime (main thread)              AppKitDispatcher + AppKitFrameClock + Recomposer + GlobalSnapshotManager
```

## 2. Package

| | |
|---|---|
| NuGet ID and assembly | `DotKtX.Compose.UI.AppKit` |
| Kotlin package | `dotktx.compose.ui.appkit` |
| Dependencies | `DotKtX.AndroidX.Compose.Runtime` (and through it the Compose compiler plugin), `DotKtX.Coroutines`, `DotKtX.AtomicFU` |
| TFM | `net10.0-macos26.0` (`DotKtXAppKitTargetFramework` in `Directory.Build.props`) |

The package's `buildTransitive` props (`DotKtX.Compose.UI.AppKit.props`) prepend
`DotKtX.Compose.UI.AppKit.Language.targets` to `AfterMicrosoftNETSdkTargets`. The .NET for macOS SDK
imports `Xamarin.Mac.$(Language).targets`, DotKt projects report the language `Kotlin`, and no such file
exists. The targets file sets `<Language>C#</Language>` for macOS projects just before the SDK's import
(`Xamarin.Mac.CSharp.targets` only sets `ResolveAssemblyConflicts` and imports the common targets). The
library and the samples import the props in their project file, because a `ProjectReference` does not get
`buildTransitive`.

Two settings in `Directory.Build.props` concern the machine, not the package:

| Property | Why |
|---|---|
| `ValidateXcodeVersion=false` | The SDK pack for platform version 26.0 is pinned; any newer Xcode builds it, and the exact Xcode version check fails with one. |
| `RuntimeIdentifier` = `osx-<host architecture>` (`DotKtXAppKitRuntimeIdentifier`) | Without a RuntimeIdentifier a Release build makes a universal (arm64 + x64) bundle whose lipo-merged libraries fail code-signature validation at launch. |

An executable also needs `ApplicationId` (the bundle identifier). The output is
`<Name>.app/Contents/MacOS/<Name>`.

## 3. Host

### 3.1 `application`

```kotlin
fun application(content: @Composable ApplicationScope.() -> Unit)
```

`application` runs on the calling thread and throws `IllegalStateException` when that is not the main
thread: AppKit needs it, and unlike WinUI there is no thread to spawn. It then:

1. `NSApplication.Init()`, activation policy `Regular`.
2. Creates the main thread's `UIRuntime` (§3.5) and an `ApplicationHost` (the `ApplicationScope`).
3. Installs a default main menu that holds only the application menu with "Quit <process name>"
   (Cmd+Q, calls `exitApplication`).
4. Composes `content` in a `Composition` over `ApplicationApplier`, which has no nodes: windows are
   effects, not nodes (§3.6). `LocalDispatchQueue` provides `DispatchQueue.MainQueue`.
5. `ActivateIgnoringOtherApps(true)` and `NSApplication.Run()`.

Exit. `exitApplication()` (or the last window closing: `windowClosed` counts windows and exits at zero)
defers its work to the main queue, because it may be called while the composition applies changes and a
composition cannot be disposed inside its own apply phase. In that turn it disposes the application
composition, calls `UIRuntime.shutdown()`, and calls `NSApplication.Stop`. `Stop` only takes effect once
an event has been processed, so it then posts an application-defined `NSEvent`; `Run()` returns, and with
it `application`.

### 3.2 Dispatcher

`DispatchQueue.asCoroutineDispatcher()` returns a `MainCoroutineDispatcher` that runs blocks with
`DispatchQueue.DispatchAsync`. Its `immediate` variant runs inline when `NSThread.IsMain`. The runtime
uses the main queue, and installs the dispatcher as `Dispatchers.Main` (`Dispatchers.setMain`), so
`LaunchedEffect` and plain `Main` code land on the AppKit thread.

### 3.3 Frame clock

`AppKitFrameClock` is a `MonotonicFrameClock` over `BroadcastFrameClock`. A frame request schedules a
one-shot `NSTimer` at 1/60 s on the main run loop in the `Common` mode (so frames also run during window
resizing and menu tracking); the callback sends the frame. No timer exists while no frame is awaited, so
an idle application does not wake up. Requests from another thread are forwarded to the main queue. Time
comes from `Stopwatch`, converted to nanoseconds without overflowing `ticks * 1e9`.

### 3.4 Global snapshot writes

Same as WinUI: `GlobalSnapshotManager` registers a global write observer and, once per turn of the
dispatcher (an atomic flag coalesces writes from any thread), calls `Snapshot.sendApplyNotifications()`.
State written outside a composition (`count++` in a click handler) therefore recomposes.

### 3.5 `UIRuntime`

An internal singleton for the main thread: dispatcher, frame clock, a `Job`, a `CoroutineScope` over them,
the `Recomposer` (running `runRecomposeAndApplyChanges` from the first use) and the snapshot manager.
`UIRuntime.forMainThread()` creates it on first use and throws off the main thread. `application` and every
`ComposeHost` share it, so state written anywhere on the main thread recomposes all of them. `shutdown()`
cancels the recomposer, the clock, the manager and the job and clears the singleton.

An exception thrown during recomposition or while changes are applied (for example a second element
in a single-child slot, §4) ends the recomposer, after which no window would update again. The runtime
therefore ends the process with `Environment.FailFast`, as an unhandled exception does: the message
and the stack trace go to the error output.

### 3.6 `Window`

```kotlin
@Composable
fun ApplicationScope.Window(
    onCloseRequest: () -> Unit,
    title: String = "",
    state: WindowState = rememberWindowState(),
    content: @Composable WindowScope.() -> Unit,
)
```

A window exists while the composable is in the composition, so several windows, and closing one, are
ordinary conditionals (`if (showSettings) Window(onCloseRequest = { showSettings = false }) { … }`).

- `remember` creates an `NSWindow` (titled, closable, miniaturizable, resizable; buffered, not deferred) and a
  `ContentHost`. `ReleasedWhenClosed` is set to `false`: the default frees the native window on close and
  leaves the managed wrapper dangling.
- A `DisposableEffect` sets `ContentView`, creates the window's `Composition` over
  `AppKitApplier(WindowRoot(contentHost))` as a child of the caller's composition context (so composition
  locals and the runtime are shared), calls `windowOpened`, applies the state before the window is shown (it
  does not open at the default size first), and `MakeKeyAndOrderFront`. Disposing orders the window out,
  disposes the composition, closes the window and calls `windowClosed`.
- `WindowShouldClose` calls the latest `onCloseRequest` and returns `false`: the close button never closes
  the window itself; the application removes it from the composition (`onCloseRequest = ::exitApplication`,
  or a state flag).
- `LocalWindow` provides the `NSWindow`; `WindowScope.window` is the same.

### 3.7 `WindowState`

`WindowState` holds `width`, `height` (the content area in points), `position` and `placement`
(`Floating` or `Maximized`) as snapshot state, and is synchronized both ways by `WindowStateSync`.

| Direction | Mechanism |
|---|---|
| State to window | `Window` reads the four values in composition; a `SideEffect` calls `apply`. Sizes within 1 point of the window's are left alone. `Maximized` calls `Zoom` when the window is not zoomed; `Floating` un-zooms first. |
| Window to state | `DidResize`, `DidMove` and `DidEndLiveResize` call `onChanged`, which writes size, placement and position back, unless `apply` is running. A miniaturized window is ignored. |

Positions use top-left coordinates: `WindowPosition.Absolute(x, y)` is the window's top-left corner in
points from the top-left corner of the main screen, y downwards, and is converted with the main screen's
height (AppKit's origin is bottom-left). `Center` calls `NSWindow.Center` and remembers the resulting
origin, so a later move by the user turns the position into `Absolute`. `PlatformDefault` centers once
when the window first appears. While zoomed, `width` and `height` keep the restored size.

### 3.8 `ComposeHost`

`open class ComposeHost : NSView` hosts Compose in an existing AppKit view tree, the counterpart of
Android's `ComposeView`. It is flipped and contains one `ContentHost` that follows its bounds.
`setContent` composes when the host is in a window (`ViewDidMoveToWindow`) and `disposeComposition` or
removal from the window disposes it, so remembered state does not survive removal. It uses the shared
`UIRuntime` and provides `LocalDispatchQueue`.

## 4. Nodes and the applier

The `Node` / `ElementNode` / `Applier` structure follows the WinUI backend (§4 there).

```kotlin
abstract class Node internal constructor(internal val children: ChildSlot)
open class ElementNode<out V : NSView> internal constructor(val view: V, children: ChildSlot)
```

`ElementNode` owns one `NSView`, the applied modifier, its `LayoutParams` (§5), the event subscriptions
(`onRelease` closes them when the node leaves the composition) and the latest callbacks. Its
`invalidateLayout()` marks the enclosing panels for layout.

`ChildSlot` is how a node holds children:

| Slot | Used by | Behavior |
|---|---|---|
| `None` | `Label`, `Button`, every control, `AppKitView` | Inserting a child throws. |
| `Single(owner, ContentHost)` | window content, `ScrollView`, `Box`, `ComposeHost` | At most one child once the changes are applied, placed in the `ContentHost`. Within one apply it can briefly hold two (content switching between two separate `if`s inserts the new element before the old one is removed); `AppKitApplier.onEndChanges` rejects a slot that still holds more than one ("`Window` content emitted 2 elements (NSTextField, NSTextField) but holds one; wrap them in a StackView"). The content parameters of these slots are marked `@AtMostOneElementContent` and element composables `@EmitsOneElement`, so `DotKtX.Compose.UI.Analyzers` reports a certain second element at build time. |
| `Many(Panel)` | `StackView` | Children of the panel in composition order. |

`AppKitApplier` inserts **bottom-up**: a new subtree is assembled while detached and joins the live view
hierarchy once, so the enclosing panels lay out once for it instead of once per descendant.
`Panel.insert` keeps `NSView` z-order equal to composition order (`AddSubview` relative to the sibling);
`move` re-adds all children in order. `AppKitView(factory, modifier, update)` places any `NSView`:
`factory` runs once, `update` runs after every recomposition of the caller.

## 5. Layout

AppKit has no XAML-style layout. An `NSView` is a rectangle that is positioned by its parent; the native
mechanisms are manual frames and Auto Layout constraints, and neither provides what a Compose backend
needs: per-child alignment and margin, minimum and maximum sizes, hidden children taking no space,
containers that size themselves from their children. WinUI gets all of that from `FrameworkElement` and
`Panel`. This backend supplies it itself, which is the main difference from the WinUI backend.

### 5.1 Panels

`Panel : NSView` is the base of every container that holds Compose children (`StackPanel` behind
`StackView`, `ContentHost`). A panel is flipped (origin top-left, y down), keeps its children as
`ElementNode`s and implements two functions, as a WinUI panel does:

- `measure(available: CGSize): CGSize`: the size it wants in `available`; an infinite or `NaN` component
  is unbounded. It includes child margins and the panel's own padding.
- `arrange(bounds: CGRect)`: sets `Frame` of each child. `Layout()` calls it with `Bounds`.

`invalidateLayout()` sets `NeedsLayout`, calls `InvalidateIntrinsicContentSize` and walks up through
panels, `ScrollPanel`s and the `NSClipView` between a scroll view and its document, so an enclosing
`StackView` grows when a label's text changes. `IntrinsicContentSize` of a panel is `measure(unbounded)`.

Children's layout properties are `LayoutParams` on the node: `alignment` (null = the parent's default),
`margin`, `padding`, `width`, `height`, `minWidth`, `maxWidth`, `minHeight`, `maxHeight`; `NaN` is unset.
The layout modifiers write them (§6) and the enclosing panel reads them.

### 5.2 Measuring a child

`measureChild(child, available)` (the caller has already removed the child's margin):

1. A hidden view measures as zero.
2. The maximum bounds clamp `available`; a fixed `width` / `height` replaces it.
3. A `Panel` is measured with `measure`. An `NSControl` with `SizeThatFits` (unbounded becomes 1e9).
   Any other view uses `IntrinsicContentSize` where it is not negative, else `FittingSize`.
4. A fixed size replaces the result; then the minimum and maximum bounds clamp it.

`arrangeChild(child, slot, defaultAlignment)` subtracts the margin from the slot, measures the child for
the rest, and places it per its alignment (`child.layout.alignment ?: defaultAlignment`). A `Stretch` axis
takes the whole slot minus margins (clamped by the bounds; a fixed size wins) instead of the measured size.
`Leading`/`Center`/`Trailing` and `Top`/`Center`/`Bottom` position the measured size in the slot. A
hidden child is skipped.

### 5.3 `ContentHost`, `StackView`, `Box`, `ScrollView`

| Composable | Behavior |
|---|---|
| `ContentHost` | At most one child, which fills it by default (`Alignment.Stretch`). The window's content view, the document of a `ScrollView`, the body of a `Box`, `ComposeHost`'s content. Because single-child slots are panels, `Modifier.align` and `margin` work on the content of a window and of a box. |
| `StackView(modifier, orientation, spacing, alignment) { }` | Children one after another along `orientation`, `spacing` points apart, hidden ones skipped. Along the main axis each child gets its measured size; the stack's main size is their sum plus spacing. Across it the child is placed by its own `Modifier.align`, else by the stack's `alignment` (`alignment.horizontal` in a vertical stack, `alignment.vertical` in a horizontal one; default `TopStart`); the stack's cross size is the largest child. |
| `Box(modifier, background, borderColor, borderWidth, cornerRadius) { }` | A layer-backed `ContentHost` with one child that fills it. `null` colors clear them. Space between the outline and the child is `Modifier.padding`. |
| `ScrollView(modifier, direction) { }` | An `NSScrollView` whose document view is a `ContentHost`. The document is as large as it wants in the scrolled directions and exactly the viewport in the others; its frame is at least the viewport. The scroll view reports the document's size as its intrinsic size, so inside a `StackView` it grows with its content: bound it with `Modifier.height(…)` or put it in a `Stretch` slot (the window's content is one). |

### 5.4 Rejected alternatives

- **Auto Layout constraints generated per child.** Per-child alignment plus margin plus bounds inside a
  stack is a set of constraints that has to be diffed and removed as children change, and ambiguity or
  conflicts show up as runtime constraint-breaking, not as a wrong frame.
- **`NSStackView`.** No per-child margin or alignment override, no `Stretch` on one axis, and hidden views
  are handled by its own distribution rules.

## 6. Modifier

The chain is the same as in WinUI: an immutable `Modifier`, `ElementModifier` elements (`key`, `apply`,
`reset`), applied by `applyModifier`, which resets what the previous chain set and the new one no longer
sets, skips equal elements (elements are data classes), and calls `invalidateLayout()` when the
`LayoutParams` changed. The modifier of a node is one `set(modifier)` in its `update` block.

| Modifier | Effect |
|---|---|
| `align(Alignment)` | `LayoutParams.alignment`; `Alignment.{TopStart … BottomEnd, Stretch}`. Start/end are leading/trailing. |
| `margin(all)`, `margin(h, v)`, `margin(start, top, end, bottom)` | `LayoutParams.margin`, points. |
| `padding(…)` | Panels only (`StackView`, `Box`, content hosts): space between the edge and the children. On any other view it has no effect and prints a warning once per view type. |
| `width`, `height`, `size` | Fixed size. |
| `widthIn(min, max)`, `heightIn(min, max)` | Bounds; `NaN` leaves one unset. |
| `alpha(Double)` | `NSView.AlphaValue` |
| `toolTip(String)` | `NSView.ToolTip` |
| `semantics(String)` | `NSView.AccessibilityLabel` |
| `testTag(String)` | `NSView.AccessibilityIdentifier` |
| `hidden(Boolean)` | `NSView.Hidden`; a hidden child takes no space in a panel. |
| `contextMenu(List<MenuEntry>)` | `NSView.Menu`, opened by right-click (§7.4). |

## 7. Wrapper composables

### 7.1 Naming and content

Composable names follow Interface Builder: `Label`, `Button`, `CheckBox` (Check Box), `RadioButton`, `Switch`,
`TextField` (Text Field), `SecureTextField`, `Slider`, `ProgressIndicator`, `PopUpButton` (Pop Up Button),
`StackView`, `ScrollView`, `Box`. AppKit controls have no content view, so buttons, check boxes and radio
buttons take a **title string** (`Button("Save", onClick = …)`), not a content lambda. Properties of one
control are parameters of its composable; properties common to every view are modifiers.

Properties are set in the node's `update` block with `set(value) { … }`, so only a changed parameter writes
to the view. Properties that change the intrinsic size (title, text, font, wrap) also call
`invalidateLayout()`.

### 7.2 Events

Events are subscribed **once**, in the node's `init`, with `.subscribe { _, _ -> }` (which returns an
`AutoCloseable`, registered with `onRelease`). The handler calls the node's current lambda, which the
`update` block replaces on recomposition, so a changing lambda never re-subscribes. `Button` uses
`Activated`; `CheckBox`, `RadioButton`, `Switch`, `Slider` and `PopUpButton` use `Activated` too;
`TextField` uses `Changed`.

### 7.3 Controlled inputs

Same contract as WinUI: the composition's value is the single source of truth. `ControlledValue` has
three operations.

- `sync(value)` runs in `reconcile` on every recomposition and writes the composed value only if the
  control holds something else, so an accepted edit is not written back.
- `userChanged(callback)` runs from the control's event: it reports the control's value and invalidates the
  wrapper's recompose scope (`currentRecomposeScope`), so the next recomposition syncs whatever the
  application decided. An edit the application rejects is restored by that sync (the digits-only field in
  the gallery).
- `programmatic { }` marks writes made by the node, whose events are ignored.

Specific to controls:

| Control | Notes |
|---|---|
| `CheckBox`, `RadioButton` | `NSButton` of type `Switch` / `Radio`, state `On`/`Off`. A radio button reports only selection (`onSelect`); the group is the application's state. |
| `Switch` | `NSSwitch.State` (1 = on). |
| `Slider` | `NSSlider`, continuous: reports on every drag step. |
| `PopUpButton` | `NSPopUpButton`, `IndexOfSelectedItem`. When `items` changes, the items are rebuilt under `programmatic` and the selection is synchronized. |
| `ProgressIndicator` | Determinate bar; display only. |
| `TextField`, `SecureTextField` | `NSTextField` / `NSSecureTextField`. `Changed` is raised synchronously with every edit and not by writing `StringValue`. While the field editor has marked (IME) text, `composing` is set, the control's text is provisional and is neither reported nor overwritten; the first `Changed` without marked text clears it and invalidates the scope. Programmatic writes keep the caret: assigning `StringValue` ends editing, so the selection of the field editor is restored, clamped to the new length. |
| `Label` | `NSTextField` as a label. `fontSize` in points (`NaN` keeps the system size); `wrap` switches between truncation and word wrapping. |

### 7.4 Menus

`MenuEntry` is `Item`, `Toggle` (check mark, controlled by `checked`), `SubMenu` and `Separator`. They
build `NSMenu`s (`AutoEnablesItems` off). A toggle reports `!checked` and shows its composed value again
until new entries arrive.

- `Modifier.contextMenu(entries)` sets `NSView.Menu` for right-click and clears it on reset.
- Menus are **application-global**: the menu bar is `NSApplication.MainMenu`, so
  `ApplicationScope.MenuBar(menus: List<MenuBarMenu>)` has no node and lives in the application
  composition. It sets the main menu to the application menu followed by `menus` and rebuilds when `menus`
  changes; when it leaves the composition the default menu returns. The application menu (with Quit)
  always comes first.

## 8. Design decisions

| Decision | Reason |
|---|---|
| `Panel : NSView` measures and arranges its children | AppKit has no per-child layout properties (§5). |
| Single-child slots are `ContentHost` panels | `align` and `margin` work for window, box and scroll content like anywhere else. |
| Layout properties live on the node, not on the view | Views other than panels do not have them; the parent reads them from the `ElementNode`. |
| Buttons take a title string | `NSButton` has no content view. |
| Main thread only; no thread spawning | AppKit requires it; `application` fails early instead of misbehaving. |
| One `UIRuntime`, shared by `application` and `ComposeHost` | State written by any AppKit code recomposes every host. |
| Windows are effects, not nodes | A window is not part of a view tree; the application composition has no nodes (`ApplicationApplier`). |
| `ReleasedWhenClosed = false` | The managed wrapper must outlive the native close. |
| The close button calls `onCloseRequest` and vetoes | Closing is the application's decision, as in Compose for Desktop. |
| Frame clock as a one-shot timer | An idle application sleeps. |
| `Common` run loop mode for the timer | Frames continue while resizing and while menus track. |
| Menu bar through a composable in `ApplicationScope` | `MainMenu` belongs to the application, not to a window. |
| No icons in menus; no lazy lists | Not in this version; `AppKitView` places any other `NSView`. |

## 9. DotKt notes

Facts verified while building this backend (DotKt SDK 0.9.34 to 0.9.36):

- **CGFloat is `NFloat`.** Pass `NFloat(x)` and read `.Value` (`System.Runtime.InteropServices.NFloat`);
  `CGRect`, `CGSize`, `CGPoint` constructors take doubles.
- **Events are `.subscribe { _, _ -> }`**, returning an `AutoCloseable`.
- **No `null` for non-null .NET parameters**: pass an object (for example `PerformClick(button)`).
  Properties declared nullable in the binding accept `null` (`view.Menu = null`).
- **`nint` is `IntPtr`**: `IntPtr(1)`, `.ToInt32()`; the binding has no empty `NSEventModifierMask`, so the
  application builds one with `ShiftKeyMask xor ShiftKeyMask`.
- Kotlin classes can subclass `NSView` and `NSScrollView` and override `Layout()`, `Tile()`,
  `IsFlipped` and `IntrinsicContentSize`.
- **Release builds.** DotKt 0.9.34 emitted invalid IL (`InvalidProgramException` when the method first
  ran) for `x ?: return`, `x as? T ?: throw …`, `x?.let { return it }` and a `when` over a sealed class
  inside a composable, because the trimmer recomputed `maxstack` without the stack depth kept across an
  early exit ([dotkt#2041](https://github.com/tmyt/dotkt/issues/2041)). DotKt 0.9.35 fixed it; the
  backend builds on 0.9.36 and the smoke test runs in Release with those constructs restored. Debug
  builds were never affected, so a Release run of the smoke test stays the check.

## 10. Source layout

```
appkit/
  src/DotKtX.Compose.UI.AppKit/
    DotKtX.Compose.UI.AppKit.ktproj
    buildTransitive/        props and Language targets (§2)
    host/                   Application, UIRuntime, AppKitDispatcher, AppKitFrameClock, GlobalSnapshotManager,
                            Window, WindowState, ComposeHost, MenuBar
    node/                   Node (ElementNode, ChildSlot), AppKitApplier, Emit, ControlledValue
    modifier/               Modifier, Modifiers (Alignment, Insets, LayoutParams, the modifiers)
    layout/                 Panel (measureChild, arrangeChild, ContentHost), StackView, Box, ScrollView
    controls/               Label, Button (CheckBox, RadioButton), TextField, Switch, Slider,
                            ProgressIndicator, PopUpButton, Menu (MenuEntry, contextMenu)
  samples/counter, samples/gallery
  tests/smoke
scripts/build-appkit.sh, scripts/smoke-appkit.sh
```

## 11. Verification

```bash
bash scripts/build-appkit.sh    # Counter, Gallery and the smoke test, Release
bash scripts/smoke-appkit.sh    # runs appkit/tests/smoke, exit code is the result
```

`appkit/tests/smoke` is an application that composes the library's composables inside the process, drives
them programmatically from inside the process and exits with a non-zero code at the first failed check.
It needs no accessibility permission, unlike a UI automation test. Debug and Release builds both run it.

The gallery (`appkit/samples/gallery`) exercises every composable: window state, menu bar, inputs, a keyed
list with per-item state, layout, a context menu, an `AppKitView` over `NSLevelIndicator` and a second
window. Every interactive view has a `testTag` (`AccessibilityIdentifier`). Running its Release build for
several seconds must print nothing: a composition error (`Error was captured in composition`) or an
exception is a defect.
