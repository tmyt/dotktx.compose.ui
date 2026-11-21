# DotKtX.Compose.UI (experimental)

Compose for native UI toolkits on DotKt. Application code is ordinary Compose (`@Composable`,
`remember`, `mutableStateOf`, effects, `CompositionLocal`); the tree Compose maintains is a tree of
native WinUI 3 elements. WinUI keeps layout, rendering, input, text, IME, accessibility and theming.
It is built directly on the Compose runtime (`DotKtX.AndroidX.Compose.Runtime`, from
`dotktx.compose`), not on `androidx.compose.ui`.

```kotlin
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Counter") {
        var count by remember { mutableIntStateOf(0) }
        Button(onClick = { count++ }, modifier = Modifier.align(Alignment.Center)) {
            TextBlock("$count")
        }
    }
}
```

| Package | Kotlin package | State |
|---|---|---|
| `DotKtX.Compose.UI.WinUI` | `dotktx.compose.ui.winui` | experimental; DotKt SDK 0.9.31 |

Design: [`docs/design-winui.md`](docs/design-winui.md).

## Status (2026-11-22, DotKt SDK 0.9.31)

`DotKtX.Compose.UI.WinUI` is its own assembly over the separately built Compose runtime DLL. All four
samples (counter, gallery, shell, interop) run on Windows and pass their UI Automation tests.

| Composables | |
|---|---|
| Host | `application`, `Window` (multiple windows, close through `onCloseRequest`, `rememberWindowState`: size, position, maximized), `ContentDialog`, `ComposeHost` (Compose inside an existing WinUI app) |
| Controls | `TextBlock`, `Button`, `HyperlinkButton`, `DropDownButton`, `TextBox`, `PasswordBox`, `NumberBox`, `CheckBox`, `RadioButton`, `ToggleSwitch`, `Slider`, `ComboBox`, `ProgressBar`, `ProgressRing`, `InfoBar`, `Image`, `WinUIElement` (any other control) |
| Layout | `StackPanel`, `Grid`, `Border`, `ScrollViewer`, `Expander`, `LazyItems` (virtualized over `ItemsRepeater`, optional keys) |
| Navigation | `TitleBar`, `NavigationView`, `TabView` |
| Menus | `MenuEntry`, `Modifier.contextMenu`, `DropDownButton` |
| Modifiers | `align`, `margin`, `width`, `height`, `size`, `widthIn`, `heightIn`, `alpha`, `gridCell`, `toolTip`, `semantics`, `testTag`, `requestedTheme`, `contextMenu`, `property` |
| Locals | `LocalWindow`, `LocalDispatcherQueue` |

Inputs are controlled: the composition's value is the source of truth, and an edit the application
does not accept is undone.

## Build

Requires .NET SDK 10 or 11, Java 21+ on `PATH`, network access for the first build, and a checkout
of `dotktx.compose` next to this repository (`../dotktx.compose`). Running the sample requires
Windows with the Windows App SDK 2.2 runtime.

```bash
bash scripts/build.sh
```

Output: `artifacts/bin/winui/samples/<counter|gallery|shell|interop>/Release/net10.0-windows10.0.19041.0/win-x64/`.
Start a sample with its `.exe` (`Counter.exe`, `Gallery.exe`). The application manifest, which makes
WinUI per-monitor (V2) DPI aware, is embedded in that apphost; `dotnet Counter.dll` runs under
`dotnet.exe`'s manifest and is DPI-unaware. Applications that reference `DotKtX.Compose.UI.WinUI`
get the same default manifest from the package's `buildTransitive` targets unless they set
`ApplicationManifest` themselves.

The Windows App SDK runtime that an unpackaged app loads at startup is the version the app itself
references (`Microsoft.WindowsAppSDK`); the same `buildTransitive` targets write it to the app's
runtimeconfig.json. Apps that start WinUI themselves call `bootstrapWindowsAppSDK()` before
`Application.Start` (see `winui/samples/interop`).

On WSL with Windows interop, `bash scripts/uia.sh counter` (or `gallery`, `shell`, `interop`) starts a sample on Windows
and runs its UI Automation test (`winui/tests/uia/`).

## Packages

An application references `DotKtX.Compose.UI.WinUI` and `Microsoft.WindowsAppSDK`. The package
brings the Compose runtime and, through it, the Compose compiler plugin
(`DotKtX.AndroidX.Compose.Compiler`), so `@Composable` code compiles without further setup. The
packages depend on the exact DotKt SDK they were built with.

```bash
bash ../dotktx.compose/scripts/pack.sh   # Compose runtime and compiler plugin packages
bash scripts/pack.sh                     # DotKtX.Compose.UI.WinUI into artifacts/packages
bash scripts/verify-packages.sh          # counter built from the packages, run with its UI Automation test
```

`scripts/verify-packages.sh` restores the packages into a fresh cache and builds
`winui/tests/package-smoke`, which takes the application manifest, the Windows App SDK version and the
compiler plugin from the packages alone.
