# DotKtX.Compose.UI.WinUI design

This document describes the design of `DotKtX.Compose.UI.WinUI`, a WinUI 3 binding for the Compose
runtime (`DotKtX.AndroidX.Compose.Runtime`, ported from compose-multiplatform-core in
`dotktx.compose`). It covers how Compose drives WinUI, the conventions that wrapper composables
follow, and the design decisions behind them. The list of available composables and the
implementation status are in the [README](../README.md).

## 1. Overview

Application code is ordinary Compose: `@Composable` functions, snapshot state, recomposition,
effects, `CompositionLocal`, slot lambdas, `Modifier` and state hoisting. The tree that Compose
maintains is a tree of **native WinUI elements** (`Microsoft.UI.Xaml.UIElement`), mutated through an
`Applier`.

WinUI keeps everything it already does well: layout (`Panel` measure and arrange), rendering, input,
text, IME, accessibility, theming and the Fluent control templates. Compose only decides **which
elements exist, in what order and with which property values**.

| | In scope | Out of scope |
|---|---|---|
| Programming model | The Compose runtime as is (`remember`, `mutableStateOf`, `LaunchedEffect`, `key`, `CompositionLocal`, …) | — |
| UI | Every WinUI control, through a wrapper composable or the `WinUIElement` interop composable | `androidx.compose.ui`, foundation and material (no `LayoutNode`, no Skia, no layout or drawing of its own) |
| Source compatibility | Runtime-level code (state holders, presenters, effects) shared with Kotlin Multiplatform Compose | UI code written against `androidx.compose.ui` |

Other libraries also run the Compose runtime over a foreign native tree: JetBrains compose-html
(DOM), Cash App Redwood (native widgets) and Mosaic (terminal).

```kotlin
import androidx.compose.runtime.*
import dotktx.compose.ui.winui.*
import System.STAThreadAttribute

@STAThreadAttribute
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Counter") {
        var count by remember { mutableIntStateOf(0) }
        Button(onClick = { count++ }, modifier = Modifier.align(Alignment.Center)) {
            TextBlock("$count")
        }
    }
}
```

The shape matches Compose for Desktop (`application { Window(onCloseRequest = ::exitApplication) { … } }`).
Only the element names reveal WinUI. Each line of the example exercises one layer:

| Code | Mechanism |
|---|---|
| `application { }` | STA thread, Windows App SDK bootstrap, `Application.Start`, the UI thread's runtime (§3.1–§3.5) |
| `Window(…) { }` | A window as a composable; a child composition rooted at `Window.Content` (§3.6) |
| `Button(…) { TextBlock(…) }` | `ComposeNode` → `ElementNode`; the single-child slot → `Button.Content` (§4) |
| `Modifier.align(…)` | A modifier element → `HorizontalAlignment` / `VerticalAlignment`, diffed (§5) |
| `onClick = { count++ }` | One `Click` subscription per element, routed to the latest lambda (§6.2) |
| `count++` | Global snapshot write → apply notification → recomposition → only `TextBlock.Text` is set (§3.4, §6.1) |

## 2. Package

| | |
|---|---|
| NuGet ID and assembly | `DotKtX.Compose.UI.WinUI` |
| Kotlin package | `dotktx.compose.ui.winui` |
| Dependencies | `DotKtX.AndroidX.Compose.Runtime` (and through it the Compose compiler plugin), `DotKtX.Coroutines`, `Microsoft.WindowsAppSDK` |
| TFM | `net10.0-windows10.0.19041.0`, `UseWinUI`, `WindowsPackageType=None` (unpackaged) |

The package's `buildTransitive` targets do two things for the application that references it:

- **Application manifest.** WinUI executables that do not set `ApplicationManifest` get a default
  manifest: per-monitor V2 DPI awareness, the Windows 10/11 `supportedOS` entries, `asInvoker` and
  Common Controls v6. The manifest is embedded in the apphost, so applications start as
  `<name>.exe`. Under `dotnet <name>.dll` the process is DPI-unaware.
- **Windows App SDK version.** The targets read `WindowsAppSDK-VersionInfo.xml` from the
  application's resolved `Microsoft.WindowsAppSDK.Runtime` package. They write the major/minor
  version, version tag and minimum runtime version to runtimeconfig.json
  (`RuntimeHostConfigurationOption`), for the bootstrap described in §3.1.

Code of this library never goes into `androidx.*`. An Avalonia backend would be
`DotKtX.Compose.UI.Avalonia` (`dotktx.compose.ui.avalonia`). Some pieces do not depend on the
toolkit: the modifier chain (§5.1), the frame-clock plumbing (§3.3) and the controlled-value helper
(§6.3). They are written so that they can move into a shared `DotKtX.Compose.UI` package.

## 3. Host

```text
application { … }                    application composition, ApplicationApplier (no-op; windows are not elements)
  └─ Window(…) { … }                 remember { Window() } + DisposableEffect
       └─ window composition         WinUIApplier rooted at Window.Content, parent = rememberCompositionContext()
            └─ Button, TextBlock, StackPanel, …   ElementNode<E>

UIRuntime (one per UI thread)        WinUIDispatcher + WinUIFrameClock + Recomposer + GlobalSnapshotManager
```

### 3.1 `application`

```kotlin
fun application(content: @Composable ApplicationScope.() -> Unit)

interface ApplicationScope {
    fun exitApplication()
}
```

1. WinUI requires an STA thread. When `main` is marked with `@STAThreadAttribute` (recommended), the
   calling thread already is one and `application` runs there. Otherwise it starts a dedicated thread
   with `ApartmentState.STA` and joins it, so an unmarked `main` still works.
2. On the STA thread it calls `bootstrapWindowsAppSDK()`, then
   `Application.Start { ComposeApplication(content) }`, then `shutdownWindowsAppSDK()`.
   - `bootstrapWindowsAppSDK()` reads the version that §2 wrote to runtimeconfig.json
     (`AppContext.GetData`).
   - It then calls `Bootstrap.Initialize(majorMinor, versionTag, minVersion, OnNoMatch_ShowUI)`, as
     the Windows App SDK's own C# auto-initializer does. If no matching runtime is installed,
     Windows offers to install it.
   - The version is the one the **application** references, so the application can update the
     Windows App SDK without a new build of this library.
   - Applications that start WinUI themselves (to use `ComposeHost`, §8) call the same pair of
     functions.
3. `ComposeApplication : Application, IXamlMetadataProvider` sets up what `App.xaml` and the XAML
   compiler would normally provide. Code-only apps have neither, and without this setup the control
   templates do not resolve their theme resources.
   - It delegates XAML type lookups to `XamlControlsXamlMetaDataProvider`.
   - It merges `XamlControlsResources` into `Application.Resources` before the first element is
     created.
4. `OnLaunched` subscribes `UnhandledException` and reports it. Exceptions that escape `OnLaunched`
   are swallowed at the WinRT boundary, and the process exits with code 0. `OnLaunched` then starts
   the application composition on the UI thread's runtime (§3.5).
5. The application exits after `exitApplication()`, or when the application composition no longer
   contains a window. Exiting runs in its own turn of the UI queue, because a composition cannot be
   disposed from inside its own apply phase. It then does the following:
   - It disposes the application composition, which closes every window through its `onDispose`.
   - It shuts the runtime down.
   - It calls `Application.Current.Exit()`.

The application composition uses `ApplicationApplier : AbstractApplier<Unit>`, whose operations do
nothing, as in Compose for Desktop. Windows are effects, not nodes (§3.6).

### 3.2 Dispatcher

`DispatcherQueue.asCoroutineDispatcher(): MainCoroutineDispatcher`

- `dispatch` uses `DispatcherQueue.TryEnqueue`. If the queue is shutting down, the job is cancelled
  and resumed elsewhere, so it completes with a cancellation instead of staying suspended forever.
- `immediate` runs inline when the caller is on the UI thread (`HasThreadAccess`).
- The first UI thread's dispatcher becomes `Dispatchers.Main` (`Dispatchers.setMain`). As a result,
  `LaunchedEffect`, `rememberCoroutineScope` and plain `Dispatchers.Main` code all run on the UI
  thread.

### 3.3 Frame clock

The `Recomposer` and `withFrameNanos` need a `MonotonicFrameClock` in the coroutine context.
`WinUIFrameClock` wraps a `BroadcastFrameClock` and sends frames from
`Microsoft.UI.Xaml.Media.CompositionTarget.Rendering`.

`Rendering` is subscribed **only while a frame is awaited**, and the subscription is released after
each frame. While any handler is attached, WinUI renders on every vsync, so a permanently
subscribed clock would keep an idle window busy. A frame can be requested from any thread. The
request moves to the UI queue before it touches `Rendering`. Frame times come from
`Stopwatch.GetTimestamp`, converted to nanoseconds without overflow.

### 3.4 Global snapshot writes

`count++` in a click handler writes to the **global** snapshot. The `Recomposer` sees the write only
when apply notifications are sent, and nothing sends them by default. Compose on Android and
Desktop solve this with a `GlobalSnapshotManager`, and so does this library.

`GlobalSnapshotManager` registers a global write observer. The observer dispatches
`Snapshot.sendApplyNotifications()` to the UI queue, behind an atomic flag. Writes from any thread
are therefore coalesced into one notification per turn of the UI queue. Without this, the counter
starts but never updates.

### 3.5 `UIRuntime`

`UIRuntime` holds the Compose machinery of one UI thread: the dispatcher, the frame clock, the
`Recomposer` (running `runRecomposeAndApplyChanges` in a scope on the dispatcher and the frame
clock) and the `GlobalSnapshotManager`.

The runtime is created on first use for the calling thread (`UIRuntime.forCurrentThread()`, keyed
by managed thread ID) and lives until it is shut down. The application composition and every
`ComposeHost` (§8) on a thread share it. As a result, state written anywhere on that thread
recomposes all of them. Using the runtime from a thread without a `DispatcherQueue` is an error.

An exception thrown during recomposition or while changes are applied (for example a second element
in a single-child slot, §4.1) ends the recomposer, after which no window of the thread would update
again. The runtime therefore ends the process with `Environment.FailFast`, as an unhandled exception
does: the message and the stack trace go to the error output and to the Windows event log.

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

A WinUI `Window` is remembered and lives while the composable is in the composition. A
`DisposableEffect` does the following:

1. It subscribes `AppWindow.Closing`.
2. It creates the window's composition: `Composition(WinUIApplier(WindowRoot(window)), parent)`,
   with `parent = rememberCompositionContext()`.
3. It sizes and places the window from `state`, then activates it.
4. On dispose, it disposes the composition and closes the window.

- **The close button does not close the window.** `Closing` is cancelled and routed to
  `onCloseRequest`. The window closes when the application removes it from the composition, which
  makes window lifetime state-driven, as in Compose for Desktop. Multiple windows and
  dialogs-as-windows follow directly from this (`if (showSettings) Window(…) { … }`).
- **CompositionLocals and invalidation flow down.** The window composition's parent is the caller's
  `CompositionContext`, so `CompositionLocal`s and snapshot invalidation flow from the application
  into every window.
- **`content` is read through `rememberUpdatedState`.** The composition is created once, but it
  always composes the latest lambda.
- **Window content.** `WindowRoot` is a `Node` whose single child slot is `Window.Content` (§4.1).
  The content composition provides `LocalWindow`.

`WindowState` holds `width`, `height`, `position` (`PlatformDefault`, `Center`, `Absolute(x, y)`)
and `placement` (`Floating`, `Maximized`) as snapshot state.

- **Both directions.** Changing a property moves or resizes the window. Moving, resizing or
  maximizing the window by hand updates the properties through `AppWindow.Changed`.
- **Units.** Sizes and positions are effective pixels. They are converted with the content's
  `XamlRoot.RasterizationScale`, so the window is placed again once the content is loaded and the
  scale is known.
- **Tolerance.** Differences within one physical pixel count as equal, so rounding does not cause
  feedback loops.
- **Maximized windows.** While a window is maximized, `width` and `height` keep its restored size.

## 4. Nodes and the applier

### 4.1 `Node` and `ElementNode`

Compose nodes are thin wrappers around WinUI elements, not the elements themselves. A wrapper is
needed for two reasons. WinUI has several unrelated child models. Each element also needs a place
for its bookkeeping: the applied modifier, its event subscriptions and the latest callbacks.

```kotlin
abstract class Node internal constructor(internal val children: ChildSlot)

open class ElementNode<out E : UIElement> internal constructor(
    val element: E,
    children: ChildSlot,
) : Node(children), ComposeNodeLifecycleCallback {
    internal var appliedModifier: Modifier = Modifier
    protected fun onRelease(action: AutoCloseable)    // closed when the node leaves the composition
}

internal sealed interface ChildSlot {
    fun insert(index: Int, child: ElementNode<*>)
    fun remove(index: Int, count: Int)
    fun move(from: Int, to: Int, count: Int)
    fun clear()

    class None(owner: String) : ChildSlot                              // leaves: TextBlock, Image, …
    class Single(owner: String, set: (UIElement?) -> Unit) : ChildSlot  // ContentControl.Content, Border.Child, Window.Content, …
    class Many(collection: UIElementCollection) : ChildSlot            // Panel.Children
}
```

The applier sees only `Node.children`. The root of a window, dialog or host composition is a plain
`Node` whose slot writes to the owner (for example `Window.Content`). Every other node is an
`ElementNode`.

- **`None`** rejects children with an error that names the owner.
- **`Single`** holds at most one child once the changes are applied. Within one apply it can briefly
  hold two: when content switches between two separate `if`s, the insert of the new element can come
  before the remove of the old one. The element is handed to the owner whenever one child is left,
  and `WinUIApplier.onEndChanges` rejects a slot that still holds more than one, with an error that
  names the owner and the children ("`Button` content emitted 2 elements (TextBlock, TextBlock) but
  holds one; wrap them in a StackPanel or Grid"). See §10, decision 2.
- **`Single.move`** and **`Many.move`** follow the contract of `AbstractApplier.move`, where `to` is
  the destination index before the items are removed. `Many` uses `UIElementCollection.Move` for a
  single item, and remove and insert for several.

### 4.2 `WinUIApplier`

`WinUIApplier : AbstractApplier<Node>` forwards insertions, removals, moves and clears to
`current.children`.

It inserts **bottom up**. Compose lets an applier build trees top down (attach a node, then its
children) or bottom up (assemble the subtree, then attach it). With bottom-up insertion, a new
subtree is fully assembled while it is detached and joins the live visual tree once. WinUI then
runs one `Loaded` and measure pass for it, instead of invalidating the parent's layout for every
descendant. Compose UI's own `UiApplier` makes the same choice.

### 4.3 Emitting

Every wrapper composable emits through `ComposeNode<N, WinUIApplier>`. The `update` block first
applies the modifier (`set(modifier) { applyModifier(it) }`) and then the wrapper's properties. Two
internal helpers cover the common shapes:

- `Leaf(factory, modifier) { … }` emits a node without children.
- `Container(factory, modifier, { … }, content)` emits a node whose children come from `content`.

The public interop composable, the equivalent of Android's `AndroidView`, places any WinUI element:

```kotlin
@Composable
fun <E : FrameworkElement> WinUIElement(
    factory: () -> E,
    modifier: Modifier = Modifier,
    update: (E) -> Unit = {},
)
```

`factory` runs once, when the element enters the composition. `update` runs after every
recomposition of the caller (`reconcile`). Application code uses `WinUIElement` for any control that
has no wrapper, so no part of WinUI is out of reach.

### 4.4 Elements owned by WinUI

Some controls decide by themselves which of their children are realized or displayed. Examples are
`TabView` (the selected tab's content), `ItemsRepeater` (the items in view) and `ContentDialog` (a
popup outside the window's tree). For these, the library does not insert Compose children directly
into the control. It uses one of two patterns:

- **Host elements.** The control is given host elements that it owns, and Compose places its element
  inside the host that is displayed.
  - `TabView` copies the selected `TabViewItem.Content` when the selection changes and does not
    follow later changes. Each tab item therefore gets a host `Border` up front, and the single
    content element moves into the `Child` of the selected item's host.
- **Subcompositions.** Content that WinUI realizes on its own schedule gets a composition of its
  own, created with the caller's `CompositionContext` as its parent. Locals and invalidation
  therefore still flow from the caller. `Window`, `ContentDialog`, `Flyout`, `TeachingTip`,
  `ComposeHost` and each realized item of the lists in §7 work this way.

## 5. Modifier

Compose separates **what an element is** (parameters) from **cross-cutting decoration**
(`Modifier`). The library keeps that split and maps it onto WinUI:

| Parameters of the composable | `Modifier` |
|---|---|
| Control-specific properties: `TextBlock(text)`, `Button(onClick, enabled)`, `StackPanel(orientation, spacing)` | Layout and decoration properties: alignment, margin, padding, size and its bounds, opacity, tooltip, automation name and ID, requested theme, context menu |

### 5.1 Chain

The library defines its own `Modifier` in `dotktx.compose.ui.winui`, not in `androidx.compose.ui`.
It has the familiar surface: the `Modifier` companion is the empty chain, elements are appended
with `then`, the chain is traversed with `foldIn`, and elements are immutable values with `equals`.

```kotlin
interface Modifier {
    fun <R> foldIn(initial: R, operation: (R, Element) -> R): R
    infix fun then(other: Modifier): Modifier
    interface Element : Modifier
    companion object : Modifier
}
```

### 5.2 Applying to an element

Each element knows how to apply itself and how to **undo** itself:

```kotlin
interface ElementModifier : Modifier.Element {
    val key: Any                                  // one entry per key; the last one in the chain wins
    fun apply(element: FrameworkElement)
    fun reset(element: FrameworkElement)
}
```

`applyModifier(new)` folds the previous and the new chain into `key → element` maps. It resets the
keys that only the previous chain had, and applies the keys whose element changed. Unchanged
elements are not applied again.

Most resets are `DependencyObject.ClearValue(property)`, which restores the style or template value
instead of guessing a default. Removing `Modifier.width(…)` therefore returns a button to its
natural width.

The general escape hatch is a modifier for any dependency property, diffed the same way:

```kotlin
Modifier.property(Control.FontSizeProperty, 24.0)     // SetValue / ClearValue
```

### 5.3 Modifiers

| Modifier | WinUI |
|---|---|
| `align(Alignment)` | `HorizontalAlignment` and `VerticalAlignment` (`Alignment.Center`, `TopStart`, …, `Stretch`) |
| `margin(all)`, `margin(horizontal, vertical)`, `margin(start, top, end, bottom)` | `Margin` |
| `padding(all)`, `padding(horizontal, vertical)`, `padding(start, top, end, bottom)` | The element type's `Padding` (see below) |
| `width`, `height`, `size`, `widthIn(min, max)`, `heightIn(min, max)` | `Width`, `Height`, `Min*`, `Max*` (`NaN` leaves a bound unset) |
| `alpha` | `Opacity` |
| `toolTip(text)` | `ToolTipService.SetToolTip` |
| `semantics(name)` | `AutomationProperties.SetName` |
| `testTag(tag)` | `AutomationProperties.SetAutomationId` (for finding elements in UI tests) |
| `requestedTheme(theme)` | `RequestedTheme` |
| `contextMenu(entries)` | `UIElement.ContextFlyout` (§6.4) |
| `property(dp, value)` | Any dependency property |

Modifiers that only mean something to one kind of parent are members of that parent's content
scope, the way Compose's `weight` belongs to `RowScope`. Outside the parent they do not compile:

| Scope | Modifier | WinUI |
|---|---|---|
| `GridScope` | `cell(row, column, rowSpan, columnSpan)` | `Grid.Row`, `Grid.Column`, `Grid.RowSpan`, `Grid.ColumnSpan` |
| `CanvasScope` | `position(left, top, zIndex)` | `Canvas.Left`, `Canvas.Top`, `Canvas.ZIndex` |
| `RelativePanelScope` | `relative(ref) { … }` | `RelativePanel.Below`, … (below) |

The scopes carry `@LayoutScopeMarker`, a `@DslMarker` like Compose's. Inside another scoped
layout's content the outer scope is not an implicit receiver, so a child of a `Canvas` inside a
`Grid` cannot take `cell`, which neither panel would use. Content lambdas without a receiver
(`StackPanel`, `Border`, a `Button`'s content) do not hide the outer scope, the same as a plain
`@Composable () -> Unit` lambda inside a Compose `Row`. `cell` outside any `Grid` content does not
compile at all.

Children of a `RelativePanel` are placed against each other, the way Compose's `ConstraintLayout`
does it. References come from `createRefs()` in the panel's scope, and `Modifier.relative(ref) { … }`
gives a child its reference and its constraints:

```kotlin
RelativePanel {
    val (title, body) = createRefs()
    TextBlock("Title", Modifier.relative(title) { alignTopWithPanel(); alignLeftWithPanel() })
    TextBlock("Body", Modifier.relative(body) { below(title); alignLeftWith(title) })
}
```

The modifier sets the element's `Name` to the reference's unique name and writes each constraint as
the target's **name**. The panel resolves names among its children during layout, so constraints do
not depend on the order in which children are emitted, and Compose never needs another node's
element. A constraint that leaves the chain is cleared like any other modifier value.

Padding is a modifier, like margin, although WinUI does not define it on `FrameworkElement`.
`Control`, `Border`, `StackPanel`, `Grid`, `RelativePanel`, `TextBlock`, `RichTextBlock`,
`ContentPresenter` and `ItemsPresenter` each define their own `Padding`, and the modifier picks the
property from the element's type. Elements without content to pad (`Image`, `Canvas`, `Viewbox`,
shapes) have none; on them the modifier has no effect and prints a warning once per element type.

The alternative, a `padding` parameter on each composable that has the property, would be checked
by the compiler but would make padding the one layout property that is not a modifier. A modifier
cannot be checked against the element's type, because `Modifier` does not know which element it is
applied to. Unlike Compose's `padding`, the modifier does not depend on its position in the chain:
it sets a property of the element, as `margin` does. Making it order-dependent would need a wrapper
element, which would move attached properties and automation IDs off the element itself.

## 6. Wrapper composables

### 6.1 Properties

```kotlin
import Microsoft.UI.Xaml.Controls.TextBlock as XamlTextBlock

@Composable
fun TextBlock(text: String, modifier: Modifier = Modifier, fontSize: Double = Double.NaN, wrap: Boolean = false) {
    Leaf({ ElementNode(XamlTextBlock(), ChildSlot.None("TextBlock")) }, modifier) {
        set(text) { element.Text = it }
        set(fontSize) { if (it.isNaN()) element.ClearValue(XamlTextBlock.FontSizeProperty) else element.FontSize = it }
        set(wrap) { element.TextWrapping = if (it) TextWrapping.Wrap else TextWrapping.NoWrap }
    }
}
```

Inside the library, a WinUI type is imported under an alias when the composable and the class share
a name (§10, decision 1).

`Updater.set` compares a value with the previous one, so a recomposition that produces the same
`text` writes nothing to WinUI. The recomposition scope is the function, and the diff is per
property. Optional properties use a sentinel (such as `NaN`) that maps to `ClearValue`, so leaving
them out keeps the style's value.

### 6.2 Events

```kotlin
internal class ButtonNode<B : ButtonBase>(button: B, owner: String) :
    ElementNode<B>(button, ChildSlot.Single(owner) { button.Content = it }) {
    var onClick: () -> Unit = {}
    init { onRelease(button.Click.subscribe { _, _ -> onClick() }) }
}

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    Container({ ButtonNode(XamlButton(), "Button") }, modifier, {
        set(onClick) { this.onClick = it }
        set(enabled) { element.IsEnabled = it }
    }, content)
}
```

An event is subscribed **once**, when the node is created, and is routed to the latest lambda.
Subscribing in `update` would stack a handler on every recomposition, and one click would then run
`onClick` several times. Subscriptions are closed in `onRelease` (§4.1). WinUI's reference tracking
can collect the cycle between an element and its delegate, but deterministic release avoids relying
on it.

Callbacks are plain lambdas, not `suspend` functions. As in Compose, code that needs to suspend uses
`rememberCoroutineScope().launch { … }` inside them.

### 6.3 Controlled inputs

Compose inputs are **controlled**: `TextBox(text = name, onTextChange = { name = it })`. The state is
the single source of truth, while the WinUI control also holds its own copy. One internal helper,
`ControlledValue`, applies the same rules to `TextBox.Text`, `PasswordBox.Password`,
`NumberBox.Value`, `CheckBox.IsChecked`, `ToggleSwitch.IsOn`, `RadioButton.IsChecked`,
`Slider.Value`, `ComboBox.SelectedIndex`, `Expander.IsExpanded`, `InfoBar.IsOpen`, and the selection
of `NavigationView` and `TabView`:

1. **Report the user's change.** The change event calls the callback with the control's value
   (`onTextChange(element.Text)`).
2. **Write only differences.** On every recomposition, the composed value is written to the control
   only when the control holds something else. An accepted edit is therefore not written back: there
   is no echo, and the caret and an active IME composition stay intact.
3. **Restore rejected edits.** If the application rejects an edit (does not update its state),
   nothing would recompose, and the control would keep the rejected value. After calling the
   callback, the node therefore invalidates the wrapper's own recompose scope. The next
   recomposition runs rule 2 with whatever the application decided, so a rejected edit is restored.
   - A check posted to the end of the UI-queue turn would not work. The recomposition that applies
     an accepted edit waits for a frame, so the check could run first and undo the accepted edit.
4. **Read the change synchronously.** The change is read from an event that is raised synchronously
   with the edit, such as `TextBox.TextChanging` (not `TextChanged`). `TextChanged` is raised later,
   and a recomposition in between would apply rule 2 to the old value and overwrite the edit before
   it is reported.
5. **Leave IME compositions alone.** While an IME composition is active, the control's text is
   provisional and is not overwritten. The scope is invalidated again when the composition ends.
6. **Ignore the node's own writes.** Change events raised while the node itself writes to the control
   are not reported.

### 6.4 Menus

`MenuEntry` (`Item`, `Toggle`, `SubMenu`, `Separator`) describes menus as data.
`Modifier.contextMenu(entries)`, `DropDownButton`, `SplitButton` and `MenuBar` turn the entries into
menu items. A flyout's items are created each time it opens, and `MenuBar`'s items when its entries
change. After a toggle item reports a click, it shows its entry's value again: an accepted change
arrives as new entries, and a rejected one does not stay checked. `CommandBar` describes its commands
the same way (`CommandBarEntry`), and its toggles follow the composed value through `ControlledValue`.

### 6.5 Icons

Every icon parameter (`TitleBar`, `InfoBadge`, `AutoSuggestBox`, `NavigationItem`, `SelectorBarEntry`,
`MenuEntry`, `CommandBarEntry`) takes `Icon`, an immutable value:

| `Icon` | WinUI |
|---|---|
| `Symbol(symbol)` | `SymbolIcon` / `SymbolIconSource` |
| `Glyph(glyph, fontFamily)` | `FontIcon` / `FontIconSource` |
| `Bitmap(uri, monochrome)` | `BitmapIcon` / `BitmapIconSource` |
| `Image(uri)` | `ImageIcon` / `ImageIconSource` |
| `Source(iconSource)` | any `IconSource`, shown through `IconSourceElement` where an element is needed |

WinUI has two forms of an icon. An `IconElement` is part of the visual tree, so it can have only one
parent; an `IconSource` describes an icon and can be shared. Some properties take one form
(`MenuFlyoutItem.Icon`), some the other (`TitleBar.IconSource`). A value type that converts to
either form, with a fresh element each time, hides the difference and compares by value, so an
unchanged icon is not applied again on recomposition. `Source` keeps every other icon reachable; it
compares by the source object, so the source is created once with `remember`.

### 6.6 Naming

Wrapper composables use **WinUI names** (`TextBlock`, `StackPanel`, `Grid`, `Border`,
`ToggleSwitch`, `NavigationView`). Parameters use Compose conventions: `onClick`, `onTextChange`,
`modifier` as the first optional parameter, and a trailing `content` slot. See §10, decision 1.

## 7. Lists

- **Small lists** use plain Compose:
  `StackPanel { for (item in items) key(item.id) { Row(item) } }`. Insertions, removals and
  reorders become `ChildSlot.Many` inserts, removals and moves.
- **Long lists** use `LazyItems(items, modifier, key) { item -> … }`, an `ItemsRepeater` with a
  `StackLayout` inside a `ScrollViewer`. Only the elements in view are created.

`LazyItems` connects Compose to `ItemsRepeater` through two WinRT interfaces implemented in Kotlin:

- **Items source.** `KeyedSource` is an `ObservableCollection` that holds one string ID per item and
  implements `IKeyIndexMapping`. Without `key`, the IDs are positions. With `key`, they are stable
  IDs assigned per user key. Every change is announced as a reset. Because the repeater can map IDs
  to indices, it keeps the elements of IDs that are still present instead of recreating them.
- **Element factory.** `ItemFactory` implements `IElementFactory`.
  - Each element is a host `Grid` with a subcomposition of its own (§4.4), whose parent is the
    list's `CompositionContext`.
  - Recycled elements go to a pool and are reused for other items.
  - The content is wrapped in `key(id)`, so a recycled element does not hand the state of its
    previous item to the next one.
  - The host is a `Grid` rather than a single-child `Border`, because when an element switches to
    another item, the new item's element is inserted before the old one is removed.
- **Updates in place.** On every recomposition of the list, the elements that the repeater keeps
  show their item's current data.

Remembered state inside an item survives reorders, inserts and removals around it (with `key`), but
not scrolling out of view. Lasting per-item state belongs in the items or above the list.
`ItemsRepeater` realizes elements against the effective viewport, which includes outer
`ScrollViewer`s, so the list needs a bounded size in its scrolling direction.

`ListView`, `GridView`, `TreeView` and `FlipView` reuse the same per-item compositions
(`ItemCompositions`), but these controls create their own containers.

- **Item template.** The `ItemTemplate` is a template whose root is an empty `Grid`. The first time
  a container is prepared, its template root becomes the item's host. The host stays with the
  container through recycling.
- **Binding.** `ListViewBase.ContainerContentChanging` is raised synchronously both when a container
  is prepared for an item and when it goes to the recycle queue, so it binds and unbinds the
  composition.
  - `TreeView` finds its internal list on `Loaded` and binds through the same event.
  - `FlipView` raises no such event. It checks its containers after each layout pass and binds the
    ones that now show a different item.
- **Change notifications.** `ListViewBase` ignores `IKeyIndexMapping` and recreates every container
  on a reset. The items source therefore reports changes as individual inserts and removals,
  computed from the keys, and falls back to a reset when most items change. Keyed rows keep their
  state across inserts and removals around them; a row that moves gets a new element.
- **Selection.** Selection is controlled: an index, a set of indices, or a key for `TreeView`.
  `TreeView` expansion is controlled through a set of keys, or left to WinUI when that set is `null`.

WinUI controls are not subclassed in Kotlin. XAML does not know the Kotlin type, so the control's
default style is not applied (for example "Cannot apply a Style with TargetType TreeView"). Wrappers
use events and template parts instead of overriding `OnApplyTemplate` or
`PrepareContainerForItemOverride`. `ComposeHost` subclasses `ContentControl`, which works because it
needs no control template of its own.

## 8. Interop

| Direction | API |
|---|---|
| A WinUI control inside Compose | `WinUIElement(factory, modifier, update)` (§4.3) |
| Compose inside an existing WinUI or XAML app | `ComposeHost : ContentControl` with `setContent { … }`, like Android's `ComposeView` |

`ComposeHost` composes its content while it is loaded (in the visual tree) and disposes the
composition when it is unloaded. Remembered state therefore does not survive removing the host from
the tree. The host uses the UI thread's shared `UIRuntime` (§3.5), so snapshot state written by
plain WinUI code, such as an event handler, recomposes it.

## 9. Popups

`Flyout` and `TeachingTip` are state-driven, like `ContentDialog`: they are shown while they are in
the composition (or while `isOpen` is true), and dismissing them only calls `onDismissRequest`.
Their content is a subcomposition (§4.4).

A popup needs a target element in another part of the composition. The target is named by a
remembered reference, as with Compose's `FocusRequester`:

```kotlin
val anchor = rememberPopupAnchor()
Button(onClick = { showHelp = true }, Modifier.popupAnchor(anchor)) { TextBlock("Help") }
if (showHelp) Flyout(anchor, onDismissRequest = { showHelp = false }) { HelpText() }
```

`Modifier.popupAnchor` stores the element in the anchor as snapshot state when the modifier is
applied. A popup that reads the anchor therefore recomposes and opens once the target exists, even
when the target lives in another composition, such as a `LazyItems` row.

## 10. Design decisions

1. **WinUI names for composables.** Compose foundation names (`Text`, `Column`) would suggest
   Compose UI semantics that WinUI does not have: a `Column` without `weight` would mislead more than
   `StackPanel` does. compose-html makes the same choice with DOM names (`Div`, `Span`) under the
   Compose programming model. The cost is a name collision with the WinUI classes. Application code
   that also imports `Microsoft.UI.Xaml.Controls.*` aliases one side. Application code rarely needs
   the classes, and `WinUIElement` factories can use fully qualified names.
2. **Several children in a single-child slot are an error.** An implicit `StackPanel` would hide a
   layout decision and add an element the application did not ask for. The error names the owner
   and the children and suggests `StackPanel` or `Grid`. Content parameters of these slots are marked
   `@AtMostOneElementContent` and element composables `@EmitsOneElement`, so the analyzers report
   content that is certain to emit a second element (`SEVERAL_ELEMENTS_IN_SINGLE_ELEMENT_CONTENT`)
   at build time. They count only marked calls at the top level of the content lambda; conditions,
   loops and unmarked composables count as zero, and those cases are left to the run-time check.
3. **Sizes are `Double` effective pixels.** Effective pixels are WinUI's unit and correspond to `dp`.
   A `Dp` value class (`8.dp`) would change every size parameter, so it waits until value classes are
   established on DotKt.
4. **`ElementNode` is generic in its element (`ElementNode<out E : UIElement>`).** Wrappers access
   `element` with its concrete type, without casts.
5. **The Windows App SDK version comes from the application.** The bootstrap uses the runtime
   version of the `Microsoft.WindowsAppSDK` package that the application references (§2, §3.1). A
   version fixed when this library is built would make every application need a new build of the
   library to update the Windows App SDK.
6. **Theming stays WinUI's.** Theme resources come from `XamlControlsResources`, and
   `Modifier.requestedTheme(…)` applies per subtree. There is no Compose-side theme system.
7. **Window and dialog lifetimes are state-driven.** Closing a window or a dialog only reports the
   request (`onCloseRequest`, `onDismissRequest`), and the application removes it from the
   composition. This keeps the composition the single source of truth, as controlled inputs do (§6.3).

## 11. CompositionLocals

| Local | Value |
|---|---|
| `LocalWindow` | The WinUI `Window` of the current window composition |
| `LocalDispatcherQueue` | The UI thread's `DispatcherQueue` |

`ContentDialog` and `TitleBar` find their window internally. Inside a `Window`, the window's
content provides the `XamlRoot` and `LocalWindow`. Inside a `ComposeHost`, the host itself provides
the `XamlRoot`.

## 12. Source layout

```text
dotktx.compose.ui/
  annotations/src/DotKtX.Compose.UI.Annotations/
                       requirements that backends declare on their API, such as @RequiresSTAThread
  analyzers/           K2 compiler plugin (FIR checkers) that reads those annotations, for kotc and the
                       Kotlin LSP; built by scripts/build-analyzers.sh and packed as
                       DotKtX.Compose.UI.Analyzers, whose buildTransitive targets register it
  analyzers/tests/     sources with expected diagnostics, checked by scripts/test-analyzers.sh
  eng/                 Analyzers.targets: registers the analyzers built from source for this repository
  winui/src/DotKtX.Compose.UI.WinUI/
    buildTransitive/   application manifest and Windows App SDK version targets
    host/              application, Window, WindowState, ContentDialog, ComposeHost, UIRuntime,
                       dispatcher, frame clock, GlobalSnapshotManager, bootstrap
    node/              Node, ElementNode, ChildSlot, WinUIApplier, emit helpers, ControlledValue
    modifier/          Modifier chain, ElementModifier, modifiers
    controls/          TextBlock, buttons, text inputs, pickers, menus, command bar, popups, WebView2, …
    layout/            panels, ScrollViewer, SplitView, LazyItems, ListView, TreeView, NavigationView, TabView, …
  winui/samples/       counter, gallery, shell, interop, inputs, pickers, collections, commands
  winui/tests/uia/     UI Automation tests for the samples
```

## 13. Verification

- **WSL and Linux.** Everything compiles (the windows TFM with `EnableWindowsTargeting`).
- **Windows.** The UI Automation tests (`winui/tests/uia/*.ps1`, run from WSL with
  `scripts/uia.sh <sample>`) drive the samples through automation patterns, without mouse input.
  The patterns used are Invoke, Value, Toggle, RangeValue, ExpandCollapse, SelectionItem, Scroll,
  ScrollItem, Window and Transform.

| Sample | Covers |
|---|---|
| `counter` | The example in §1, closing through `onCloseRequest` |
| `gallery` | Controls and layouts; controlled inputs, including accepted and rejected edits; keyed list moves with remembered per-item state; content switching between separate `if`s in a single-child slot; modifiers; menus; `LazyItems`, with and without keys; `WindowState`; `ContentDialog`; a second window |
| `shell` | `TitleBar`, `NavigationView` (controlled selection and pane), `TabView` (selection, close, add), `Expander` |
| `interop` | A plain WinUI application that hosts Compose through `ComposeHost` and shares state with WinUI code |
| `inputs` | Toggle, repeat and split buttons, `RatingControl`, `ColorPicker`, `AutoSuggestBox` (typing, rejected edits, suggestions, query), `RichEditBox` |
| `pickers` | Date and time pickers, `CalendarView`, `PipsPager`, `SplitView`, `Viewbox`, `Canvas`, `RelativePanel` (constraints changed by state), `InfoBadge`, `PersonPicture` |
| `collections` | `ListView` and `GridView` (selection, keyed inserts), `TreeView` (selection, expansion), `FlipView`, `BreadcrumbBar`, `SelectorBar` |
| `commands` | `CommandBar` (buttons, toggles, overflow), `MenuBar`, `Flyout`, `TeachingTip`, `WebView2` (navigation both ways) |

UI Automation searches stay inside the sample's own windows: the test window, or the process's
top-level windows for popups. Searching the whole desktop walks every other application's UI.

Behavior is verified against real WinUI rather than a fake element tree. The problems a binding has
to solve come from WinUI's own behavior: `TabView` copying the selected item's content,
`ItemsRepeater` realizing against outer viewports, `TextChanged` arriving after the edit. A fake
tree would not reproduce them.
