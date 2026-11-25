# UI Automation test for the gallery sample. Start the sample first; this script finds its window,
# drives the controls through automation patterns and closes the application.
# Prints PASS/FAIL per check and exits 1 if any check fails.
Add-Type -AssemblyName UIAutomationClient,UIAutomationTypes
$A = [System.Windows.Automation.AutomationElement]
$Scope = [System.Windows.Automation.TreeScope]
$failures = 0

function Cond($property, $value) { New-Object System.Windows.Automation.PropertyCondition($property, $value) }
function Pattern($element, $pattern) { $element.GetCurrentPattern($pattern::Pattern) }

function Find-Window($title, $seconds = 30) {
  for ($t = 0; $t -lt $seconds * 4; $t++) {
    $w = $A::RootElement.FindFirst($Scope::Children, (Cond $A::NameProperty $title))
    if ($w) { return $w }
    Start-Sleep -Milliseconds 250
  }
  return $null
}

# An element can appear a layout pass after the action that realizes it (for example a list row after
# ScrollIntoView), so lookups retry briefly.
function Find-Id($root, $id) {
  for ($t = 0; $t -lt 10; $t++) {
    $e = $root.FindFirst($Scope::Descendants, (Cond $A::AutomationIdProperty $id))
    if ($e) { return $e }
    Start-Sleep -Milliseconds 150
  }
  return $null
}

# Waits until $probe returns $expected (UI updates land after a frame), then records the result.
function Check($label, $expected, [scriptblock]$probe) {
  $actual = $null
  for ($t = 0; $t -lt 20; $t++) {
    try { $actual = & $probe } catch { $actual = "ERROR: $($_.Exception.Message)" }
    if ($actual -eq $expected) { break }
    Start-Sleep -Milliseconds 150
  }
  if ($actual -eq $expected) { "PASS $label" } else { $script:failures++; "FAIL $label`n     expected [$expected]`n     actual   [$actual]" }
}

function Text($root, $id) { (Find-Id $root $id).Current.Name }

$win = Find-Window 'Gallery'
if (-not $win) { "FAIL no Gallery window"; exit 1 }
$processId = $win.Current.ProcessId

# Window state: the application sizes, moves and maximizes the window, and user changes flow back.
Add-Type @"
using System; using System.Runtime.InteropServices;
public static class WinDpi { [DllImport("user32.dll")] public static extern uint GetDpiForWindow(IntPtr hwnd); }
"@
$scale = [WinDpi]::GetDpiForWindow([IntPtr]$win.Current.NativeWindowHandle) / 96.0
function Near($a, $b) { if ([Math]::Abs($a - $b) -le 2) { 'near' } else { "$a vs $b" } }
function Bounds() { $win.Current.BoundingRectangle }
$windowPattern = Pattern $win ([System.Windows.Automation.WindowPattern])
Check 'initial window state' 'window=720x640 Floating at center' { Text $win 'windowEcho' }
Check 'initial width in physical pixels' 'near' { Near (Bounds).Width (720 * $scale) }
(Pattern (Find-Id $win 'wider') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'state width resizes window' 'near' { Near (Bounds).Width (820 * $scale) }
Check 'state width echo' 'window=820x640 Floating at center' { Text $win 'windowEcho' }
(Pattern (Find-Id $win 'moveTo') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'state position moves window' 'near' { Near (Bounds).X (100 * $scale) }
(Pattern $win ([System.Windows.Automation.TransformPattern])).Resize([Math]::Round(900 * $scale), [Math]::Round(600 * $scale))
Check 'user resize updates state' 'window=900x600 Floating at 100,80' { Text $win 'windowEcho' }
(Pattern $win ([System.Windows.Automation.TransformPattern])).Move([Math]::Round(200 * $scale), [Math]::Round(120 * $scale))
Check 'user move updates state' 'window=900x600 Floating at 200,120' { Text $win 'windowEcho' }
(Pattern (Find-Id $win 'maximize') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'state placement maximizes' 'Maximized' { [string]$windowPattern.Current.WindowVisualState }
Check 'maximized keeps restored size' 'window=900x600 Maximized at 200,120' { Text $win 'windowEcho' }
$windowPattern.SetWindowVisualState([System.Windows.Automation.WindowVisualState]::Normal)
Check 'user restore updates state' 'window=900x600 Floating at 200,120' { Text $win 'windowEcho' }
$windowPattern.SetWindowVisualState([System.Windows.Automation.WindowVisualState]::Maximized)
Check 'user maximize updates state' 'window=900x600 Maximized at 200,120' { Text $win 'windowEcho' }
(Pattern (Find-Id $win 'restore') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'state placement restores' 'Normal' { [string]$windowPattern.Current.WindowVisualState }
Check 'restored size' 'near' { Near (Bounds).Width (900 * $scale) }

# Controlled TextBox: accepted edits flow into state.
(Pattern (Find-Id $win 'name') ([System.Windows.Automation.ValuePattern])).SetValue('Ann')
Check 'TextBox edit reaches state' 'Hello, Ann' { Text $win 'greeting' }

# Controlled TextBox: rejected edits are undone.
$digits = Find-Id $win 'digits'
(Pattern $digits ([System.Windows.Automation.ValuePattern])).SetValue('12')
Check 'TextBox accepts digits' 'digits=12' { Text $win 'digitsEcho' }
(Pattern $digits ([System.Windows.Automation.ValuePattern])).SetValue('12a')
Check 'TextBox rejected edit restored' '12' { (Pattern $digits ([System.Windows.Automation.ValuePattern])).Current.Value }
Check 'TextBox rejected edit keeps state' 'digits=12' { Text $win 'digitsEcho' }

(Pattern (Find-Id $win 'agree') ([System.Windows.Automation.TogglePattern])).Toggle()
Check 'CheckBox' 'agreed=yes' { Text $win 'agreeEcho' }

(Pattern (Find-Id $win 'wifi') ([System.Windows.Automation.TogglePattern])).Toggle()
Check 'ToggleSwitch' 'wifi=no' { Text $win 'wifiEcho' }

(Pattern (Find-Id $win 'volume') ([System.Windows.Automation.RangeValuePattern])).SetValue(75)
Check 'Slider' 'volume=75' { Text $win 'volumeEcho' }
Check 'ProgressBar follows state' '75' { [string](Pattern (Find-Id $win 'progress') ([System.Windows.Automation.RangeValuePattern])).Current.Value }

$size = Find-Id $win 'size'
(Pattern $size ([System.Windows.Automation.ExpandCollapsePattern])).Expand()
Start-Sleep -Milliseconds 500
$large = $size.FindFirst($Scope::Descendants, (Cond $A::NameProperty 'Large'))
if ($large) { (Pattern $large ([System.Windows.Automation.SelectionItemPattern])).Select() }
Check 'ComboBox' 'size=Large' { Text $win 'sizeEcho' }

# More controls.
(Pattern (Find-Id $win 'radioB') ([System.Windows.Automation.SelectionItemPattern])).Select()
Check 'RadioButton selects' 'choice=B' { Text $win 'choiceEcho' }
Check 'RadioButton deselects others' 'False' { [string](Pattern (Find-Id $win 'radioA') ([System.Windows.Automation.SelectionItemPattern])).Current.IsSelected }

(Pattern (Find-Id $win 'link') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'HyperlinkButton' 'links=1' { Text $win 'linkEcho' }

(Pattern (Find-Id $win 'quantity') ([System.Windows.Automation.RangeValuePattern])).SetValue(42)
Check 'NumberBox' 'quantity=42' { Text $win 'quantityEcho' }
(Pattern (Find-Id $win 'quantity') ([System.Windows.Automation.RangeValuePattern])).SetValue(500)
Check 'NumberBox clamps to maximum' 'quantity=99' { Text $win 'quantityEcho' }

(Pattern (Find-Id $win 'password') ([System.Windows.Automation.ValuePattern])).SetValue('hunter2')
Check 'PasswordBox' 'passwordLength=7' { Text $win 'passwordEcho' }

Check 'ProgressRing present' 'ring' { (Find-Id $win 'ring').Current.AutomationId }

(Pattern (Find-Id $win 'showNotice') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'InfoBar opens' 'notice=yes' { Text $win 'noticeEcho' }
$notice = Find-Id $win 'notice'
$close = if ($notice) { $notice.FindFirst($Scope::Descendants, (Cond $A::ControlTypeProperty ([System.Windows.Automation.ControlType]::Button))) } else { $null }
if ($close) { (Pattern $close ([System.Windows.Automation.InvokePattern])).Invoke() }
Check 'InfoBar close button dismisses' 'notice=no' { Text $win 'noticeEcho' }

# ContentDialog: buttons report to the application, which closes the dialog by removing it.
function Dialog-Button($name) { $win.FindFirst($Scope::Descendants, (New-Object System.Windows.Automation.AndCondition((Cond $A::ControlTypeProperty ([System.Windows.Automation.ControlType]::Button)), (Cond $A::NameProperty $name)))) }
(Pattern (Find-Id $win 'ask') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'dialog opens' 'Delete' { (Dialog-Button 'Delete').Current.Name }
(Pattern (Find-Id $win 'reason') ([System.Windows.Automation.ValuePattern])).SetValue('old')
(Pattern (Dialog-Button 'Delete') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'dialog primary with content state' 'dialog=deleted:old' { Text $win 'dialogEcho' }
Check 'dialog closed' 'closed' { if (Dialog-Button 'Delete') { 'open' } else { 'closed' } }
(Pattern (Find-Id $win 'ask') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'dialog reopens' 'Cancel' { (Dialog-Button 'Cancel').Current.Name }
(Pattern (Dialog-Button 'Cancel') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'dialog close button dismisses' 'dialog=cancelled' { Text $win 'dialogEcho' }
Check 'dialog closed again' 'closed' { if (Dialog-Button 'Cancel') { 'open' } else { 'closed' } }

# Menus: entries are rebuilt from the composed state each time a menu opens.
# Menus open as top-level popups of the process; searching the whole desktop instead would walk
# every other application's UI.
function Menu-Item($name) {
  for ($t = 0; $t -lt 20; $t++) {
    foreach ($top in $A::RootElement.FindAll($Scope::Children, (Cond $A::ProcessIdProperty $processId))) {
      $m = $top.FindFirst($Scope::Descendants, (Cond $A::NameProperty $name))
      if ($m) { return $m }
    }
    Start-Sleep -Milliseconds 150
  }
  return $null
}
$dropDown = Pattern (Find-Id $win 'dropDown') ([System.Windows.Automation.ExpandCollapsePattern])
# A menu that is still closing ignores Expand, so retry until the wanted entry shows up.
function Open-Menu($name) {
  for ($try = 0; $try -lt 5; $try++) {
    try { $dropDown.Expand() } catch {}
    $m = Menu-Item $name
    if ($m) { return $m }
  }
  return $null
}
(Pattern (Open-Menu 'Copy') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'DropDownButton menu item' 'menu=copy wrap=no' { Text $win 'menuEcho' }
$wrapItem = Open-Menu 'Word wrap'
Check 'toggle entry shows composed state' 'Off' { [string](Pattern $wrapItem ([System.Windows.Automation.TogglePattern])).Current.ToggleState }
(Pattern $wrapItem ([System.Windows.Automation.TogglePattern])).Toggle()
Check 'toggle entry' 'menu=copy wrap=yes' { Text $win 'menuEcho' }
$wrapItem = Open-Menu 'Word wrap'
Check 'toggle entry reopens checked' 'On' { [string](Pattern $wrapItem ([System.Windows.Automation.TogglePattern])).Current.ToggleState }
(Pattern (Menu-Item 'Export') ([System.Windows.Automation.ExpandCollapsePattern])).Expand()
(Pattern (Menu-Item 'SVG') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'submenu item' 'menu=export:svg wrap=yes' { Text $win 'menuEcho' }
Add-Type -AssemblyName System.Windows.Forms
# Shift+F10 is real keyboard input: it needs the window in the foreground, and Windows may refuse it
# (for example while another window holds the foreground), which then fails only this check.
try {
  (Find-Id $win 'contextTarget').SetFocus()
  Start-Sleep -Milliseconds 300
  [System.Windows.Forms.SendKeys]::SendWait('+{F10}')
} catch { "NOTE could not send Shift+F10: $($_.Exception.Message)" }
$contextItem = Menu-Item 'Context action'
if ($contextItem) { (Pattern $contextItem ([System.Windows.Automation.InvokePattern])).Invoke() }
Check 'context menu' 'menu=context wrap=yes' { Text $win 'menuEcho' }

# Keyed list: per-item remembered state follows its item through reorders and removals.
$add = Pattern (Find-Id $win 'add') ([System.Windows.Automation.InvokePattern])
for ($i = 0; $i -lt 3; $i++) { $add.Invoke(); Start-Sleep -Milliseconds 200 }
Check 'list add' 'count=3' { Text $win 'count' }
Check 'item 2 present' 'Item 2' { (Find-Id $win 'done2').Current.Name }
(Pattern (Find-Id $win 'done2') ([System.Windows.Automation.TogglePattern])).Toggle()
(Pattern (Find-Id $win 'reverse') ([System.Windows.Automation.InvokePattern])).Invoke()
# Panels have no automation peer, so the list's check boxes are found in the window, in tree order.
$order = {
  $boxes = $win.FindAll($Scope::Descendants, (Cond $A::ControlTypeProperty ([System.Windows.Automation.ControlType]::CheckBox)))
  ($boxes | ForEach-Object { $_.Current.AutomationId } | Where-Object { $_ -like 'done*' }) -join ','
}
Check 'list reversed' 'done3,done2,done1' $order
Check 'item 2 still checked after reverse' 'On' { [string](Pattern (Find-Id $win 'done2') ([System.Windows.Automation.TogglePattern])).Current.ToggleState }
(Pattern (Find-Id $win 'removeFirst') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'list remove first' 'done2,done1' $order
Check 'item 2 still checked after remove' 'On' { [string](Pattern (Find-Id $win 'done2') ([System.Windows.Automation.TogglePattern])).Current.ToggleState }
Check 'item 1 unchecked' 'Off' { [string](Pattern (Find-Id $win 'done1') ([System.Windows.Automation.TogglePattern])).Current.ToggleState }

# LazyItems: only visible rows are realized; scrolling, data changes and count changes.
function Realized() {
  $texts = $win.FindAll($Scope::Descendants, (Cond $A::ControlTypeProperty ([System.Windows.Automation.ControlType]::Text)))
  @($texts | Where-Object { $_.Current.AutomationId -like 'row*' }).Count
}
# ItemsRepeater realizes against the effective viewport, including the gallery's own scroll viewer,
# so the list has to be on screen first.
(Pattern (Find-Id $win 'lazy') ([System.Windows.Automation.ScrollItemPattern])).ScrollIntoView()
Check 'first row realized' 'Row 0' { Text $win 'row0' }
Check 'last row not realized' 'absent' { if (Find-Id $win 'row9999') { 'present' } else { 'absent' } }
Check 'few rows realized' 'few' { $n = Realized; if ($n -gt 3 -and $n -lt 100) { 'few' } else { "count $n" } }
$lazyScroll = Pattern (Find-Id $win 'lazy') ([System.Windows.Automation.ScrollPattern])
$lazyScroll.SetScrollPercent([System.Windows.Automation.ScrollPattern]::NoScroll, 100)
Check 'scrolled to last row' 'Row 9999' { Text $win 'row9999' }
Check 'first row recycled' 'absent' { if (Find-Id $win 'row0') { 'present' } else { 'absent' } }
(Pattern (Find-Id $win 'renameRows') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'data change updates visible rows' 'Item 9999' { Text $win 'row9999' }
(Pattern (Find-Id $win 'removeRow') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'count change removes row' 'absent' { if (Find-Id $win 'row9999') { 'present' } else { 'absent' } }
$lazyScroll.SetScrollPercent([System.Windows.Automation.ScrollPattern]::NoScroll, 100)
Check 'new last row' 'Item 9998' { Text $win 'row9998' }
$lazyScroll.SetScrollPercent([System.Windows.Automation.ScrollPattern]::NoScroll, 0)
Check 'back to top' 'Item 0' { Text $win 'row0' }

# Keyed LazyItems: remembered state of a realized row follows its key through reorders, inserts and
# removals (all rows stay in view; a row scrolled out of view is recycled and starts fresh).
function Star($id) { [string](Pattern (Find-Id $win "star$id") ([System.Windows.Automation.TogglePattern])).Current.ToggleState }
function KeyedOrder() {
  $boxes = (Find-Id $win 'keyedLazy').FindAll($Scope::Descendants, (Cond $A::ControlTypeProperty ([System.Windows.Automation.ControlType]::CheckBox)))
  @($boxes | Sort-Object { $_.Current.BoundingRectangle.Y } | ForEach-Object { $_.Current.AutomationId -replace 'star','' }) -join ','
}
(Pattern (Find-Id $win 'keyedLazy') ([System.Windows.Automation.ScrollItemPattern])).ScrollIntoView()
(Pattern (Find-Id $win 'star2') ([System.Windows.Automation.TogglePattern])).Toggle()
Check 'keyed row starred' 'On' { Star 2 }
(Pattern (Find-Id $win 'reversePeople') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'keyed reverse order on screen' '6,5,4,3,2,1' { KeyedOrder }
Check 'star follows its row after reverse' 'On' { Star 2 }
Check 'neighbour not starred after reverse' 'Off' { Star 1 }
(Pattern (Find-Id $win 'reversePeople') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'star follows its row after reversing back' 'On' { Star 2 }
(Pattern (Find-Id $win 'insertPerson') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'keyed insert order on screen' '7,1,2,3,4,5,6' { KeyedOrder }
Check 'inserted row starts unstarred' 'Off' { Star 7 }
Check 'star kept after insert' 'On' { Star 2 }
(Pattern (Find-Id $win 'removePerson') ([System.Windows.Automation.InvokePattern])).Invoke()
(Pattern (Find-Id $win 'removePerson') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'keyed remove' 'people=2,3,4' { Text $win 'peopleEcho' }
Check 'star kept after removals' 'On' { Star 2 }

# Layout and modifiers.
Check 'Grid cell' 'Grid cell 0,1' { $win.FindFirst($Scope::Descendants, (Cond $A::NameProperty 'Grid cell 0,1')).Current.Name }
Check 'semantics name' 'bordered text' { $win.FindFirst($Scope::Descendants, (Cond $A::NameProperty 'bordered text')).Current.Name }

# Modifier.padding, in effective pixels. TextBlock has no padding of its own, so its height grows by
# exactly top + bottom; the Button returns to its style's padding when the modifier goes away; the
# panel's top padding moves its child down relative to the Button above it.
# The padding examples sit at the end of the gallery; elements outside the viewport report no bounds,
# so every measurement first scrolls the gallery to its end.
$galleryScroll = Pattern (Find-Id $win 'galleryScroll') ([System.Windows.Automation.ScrollPattern])
function Rect($id) {
  $galleryScroll.SetScrollPercent([System.Windows.Automation.ScrollPattern]::NoScroll, 100)
  Start-Sleep -Milliseconds 100
  (Find-Id $win $id).Current.BoundingRectangle
}
function Dip($value) { [Math]::Round($value / $scale) }
$padToggle = Pattern (Find-Id $win 'padToggle') ([System.Windows.Automation.TogglePattern])
$text0 = Rect 'padText'; $button0 = Rect 'padButton'; $child0 = Rect 'padPanelChild'
$padToggle.Toggle()
Check 'padding grows TextBlock by top + bottom' 48 { Dip ((Rect 'padText').Height - $text0.Height) }
Check 'padding replaces the Button style padding' 'True' { [string]((Rect 'padButton').Height -gt $button0.Height + 20 * $scale) }
Check 'panel padding moves its child' 24 { Dip (((Rect 'padPanelChild').Y - (Rect 'padButton').Bottom) - ($child0.Y - $button0.Bottom)) }
$padToggle.Toggle()
Check 'removing padding restores TextBlock' 'near' { Near (Rect 'padText').Height $text0.Height }
Check 'removing padding restores the Button style padding' 'near' { Near (Rect 'padButton').Height $button0.Height }

# A second window opens from state and closes through onCloseRequest.
(Pattern (Find-Id $win 'openSettings') ([System.Windows.Automation.InvokePattern])).Invoke()
$settings = Find-Window 'Settings' 10
Check 'second window opens' 'Settings window' { Text $settings 'settingsText' }
if ($settings) { (Pattern $settings ([System.Windows.Automation.WindowPattern])).Close() }
Check 'second window closes' 'closed' { if ($A::RootElement.FindFirst($Scope::Children, (Cond $A::NameProperty 'Settings'))) { 'open' } else { 'closed' } }
Check 'main window stays' 'Gallery' { $win.Current.Name }

(Pattern $win ([System.Windows.Automation.WindowPattern])).Close()
Check 'application exits' 'exited' { if (Get-Process -Id $processId -ErrorAction SilentlyContinue) { 'running' } else { 'exited' } }

if ($failures -eq 0) { "ALL PASS" } else { "FAILURES $failures"; exit 1 }
