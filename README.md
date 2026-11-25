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
| `DotKtX.Compose.UI.WinUI` | `dotktx.compose.ui.winui` | experimental; DotKt SDK 0.9.33 |

Design: [`docs/design-winui.md`](docs/design-winui.md).

## Status (2026-11-25, DotKt SDK 0.9.34)

`DotKtX.Compose.UI.WinUI` is its own assembly over the separately built Compose runtime DLL. All
eight samples (counter, gallery, shell, interop, inputs, pickers, collections, commands) run on
Windows and pass their UI Automation tests.

| Composables | |
|---|---|
| Host | `application`, `Window` (multiple windows, close through `onCloseRequest`, `rememberWindowState`: size, position, maximized), `ContentDialog`, `ComposeHost` (Compose inside an existing WinUI app) |
| Text and buttons | `TextBlock`, `Button`, `HyperlinkButton`, `ToggleButton`, `RepeatButton`, `DropDownButton`, `SplitButton`, `ToggleSplitButton` |
| Inputs | `TextBox`, `PasswordBox`, `NumberBox`, `AutoSuggestBox`, `RichEditBox` (uncontrolled), `CheckBox`, `RadioButton`, `ToggleSwitch`, `Slider`, `ComboBox`, `RatingControl`, `ColorPicker` |
| Dates and times | `DatePicker`, `TimePicker`, `CalendarDatePicker`, `CalendarView` |
| Status | `ProgressBar`, `ProgressRing`, `InfoBar`, `InfoBadge`, `PersonPicture`, `Image` |
| Layout | `StackPanel`, `Grid` (children take `Modifier.cell(row, column)`), `Border`, `ScrollViewer`, `Viewbox`, `Canvas` (children take `Modifier.position(left, top)`), `RelativePanel` (the XAML counterpart of `ConstraintLayout`: `createRefs()` and `Modifier.relative(ref) { below(title) }`), `SplitView`, `Expander` |
| Collections | `LazyItems` (virtualized over `ItemsRepeater`), `ListView`, `GridView` (single, multiple or no selection), `TreeView`, `FlipView`, `PipsPager`; optional keys keep per-item state |
| Navigation | `TitleBar`, `NavigationView`, `TabView`, `BreadcrumbBar`, `SelectorBar` |
| Commands and menus | `CommandBar` (`CommandBarEntry`), `MenuBar` (`MenuBarMenu`), `MenuEntry`, `Modifier.contextMenu` |
| Popups | `Flyout`, `TeachingTip`, anchored with `rememberPopupAnchor` and `Modifier.popupAnchor` |
| Web | `WebView2` |
| Interop | `WinUIElement` (any other control) |
| Icons | `Icon`: `Symbol`, `Glyph` (font glyph), `Bitmap`, `Image`, `Source` (any WinUI `IconSource`), taken by every icon parameter |
| Modifiers | `align`, `margin`, `padding`, `width`, `height`, `size`, `widthIn`, `heightIn`, `alpha`, `toolTip`, `semantics`, `testTag`, `requestedTheme`, `contextMenu`, `popupAnchor`, `property` |
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

Output: `artifacts/bin/winui/samples/<sample>/Release/net10.0-windows10.0.19041.0/win-x64/`.
Start a sample with its `.exe` (`Counter.exe`, `Gallery.exe`). The application manifest, which makes
WinUI per-monitor (V2) DPI aware, is embedded in that apphost; `dotnet Counter.dll` runs under
`dotnet.exe`'s manifest and is DPI-unaware. Applications that reference `DotKtX.Compose.UI.WinUI`
get the same default manifest from the package's `buildTransitive` targets unless they set
`ApplicationManifest` themselves.

The Windows App SDK runtime that an unpackaged app loads at startup is the version the app itself
references (`Microsoft.WindowsAppSDK`); the same `buildTransitive` targets write it to the app's
runtimeconfig.json. Apps that start WinUI themselves call `bootstrapWindowsAppSDK()` before
`Application.Start` (see `winui/samples/interop`).

On WSL with Windows interop, `bash scripts/uia.sh <sample>` (for example `counter`) starts a sample on Windows
and runs its UI Automation test (`winui/tests/uia/`).

## Packages

An application references `DotKtX.Compose.UI.WinUI` and `Microsoft.WindowsAppSDK`. The package
brings the Compose runtime and, through it, the Compose compiler plugin
(`DotKtX.AndroidX.Compose.Compiler`), so `@Composable` code compiles without further setup. The
packages depend on the exact DotKt SDK they were built with.

A complete application project:

```xml
<Project Sdk="DotKt.Sdk">
  <PropertyGroup>
    <OutputType>Exe</OutputType>
    <!-- net10.0-windows10.0.19041.0 or any later Windows version, such as 10.0.26100.0. -->
    <TargetFramework>net10.0-windows10.0.19041.0</TargetFramework>
    <UseWinUI>true</UseWinUI>
    <WindowsPackageType>None</WindowsPackageType>
    <!-- The application manifest (DPI awareness) is embedded in the <name>.exe apphost. -->
    <RuntimeIdentifier>win-x64</RuntimeIdentifier>
    <SelfContained>false</SelfContained>
    <!-- The class that holds `fun main()`: MainKt for Main.kt. -->
    <StartupObject>MainKt</StartupObject>
  </PropertyGroup>
  <ItemGroup>
    <PackageReference Include="DotKtX.Compose.UI.WinUI" Version="0.3.1" />
    <PackageReference Include="Microsoft.WindowsAppSDK" Version="2.2.0" />
  </ItemGroup>
</Project>
```

with `global.json` pinning the DotKt SDK the packages were built with:

```json
{ "msbuild-sdks": { "DotKt.Sdk": "0.9.33" } }
```

The library is compiled against the Windows SDK projection that .NET SDK 10 selects
(`WindowsSdkPackageVersion` 10.0.19041.57, WinRT.Runtime 2.2.0.0) rather than the newer one .NET SDK 11
selects, so applications built with either provide the WinRT.Runtime version it references or a newer
one.

```bash
bash ../dotktx.compose/scripts/pack.sh   # Compose runtime and compiler plugin packages
bash scripts/pack.sh                     # DotKtX.Compose.UI.WinUI into artifacts/packages
bash scripts/verify-packages.sh          # counter built from the packages, run with its UI Automation test
```

`scripts/verify-packages.sh` restores the packages into a fresh cache and builds
`winui/tests/package-smoke` as an application outside the repository would: empty
`Directory.Build.*` files shadow the repository's, and its `global.json` selects .NET SDK 10. The
application manifest, the Windows App SDK version and the compiler plugin come from the packages alone,
and a reference conflict warning (MSB3277) fails the check.
