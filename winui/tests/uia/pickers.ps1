# UI Automation test for the pickers sample: DatePicker, TimePicker, CalendarDatePicker, CalendarView,
# InfoBadge, PersonPicture, PipsPager, Viewbox, Canvas, RelativePanel and SplitView.
# Start the sample first. Prints PASS/FAIL per check and exits 1 if any check fails.
Add-Type -AssemblyName UIAutomationClient,UIAutomationTypes
$A = [System.Windows.Automation.AutomationElement]
$Scope = [System.Windows.Automation.TreeScope]
$Invoke = [System.Windows.Automation.InvokePattern]
$SelectionItem = [System.Windows.Automation.SelectionItemPattern]
$failures = 0

function Cond($property, $value) { New-Object System.Windows.Automation.PropertyCondition($property, $value) }
function Pattern($element, $pattern) { $element.GetCurrentPattern($pattern::Pattern) }
function Find-Id($root, $id) { $root.FindFirst($Scope::Descendants, (Cond $A::AutomationIdProperty $id)) }
function Text($root, $id) { (Find-Id $root $id).Current.Name }
function Invoke-Id($root, $id) { (Pattern (Find-Id $root $id) $Invoke).Invoke() }

# Elements that are not control elements (PersonPicture) are only found in the raw view.
function Find-Raw($root, $id) {
  $request = New-Object System.Windows.Automation.CacheRequest
  $request.TreeFilter = [System.Windows.Automation.Automation]::RawViewCondition
  $request.Push()
  try { $root.FindFirst($Scope::Descendants, (Cond $A::AutomationIdProperty $id)) } finally { $request.Pop() }
}

# Picker texts carry Unicode direction marks around each date part.
function Plain($s) { $s -replace "[$([char]0x200E)$([char]0x200F)]", '' }

# First descendant of $root whose name, without direction marks, is $name.
function Find-Plain($root, $name) {
  foreach ($e in $root.FindAll($Scope::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
    if ((Plain $e.Current.Name) -eq $name) { return $e }
  }
  return $null
}

function Check($label, $expected, [scriptblock]$probe) {
  $actual = $null
  for ($t = 0; $t -lt 20; $t++) {
    try { $actual = & $probe } catch { $actual = "ERROR: $($_.Exception.Message)" }
    if ($actual -eq $expected) { break }
    Start-Sleep -Milliseconds 150
  }
  if ($actual -eq $expected) { "PASS $label" } else { $script:failures++; "FAIL $label`n     expected [$expected]`n     actual   [$actual]" }
}

$win = $null
for ($t = 0; $t -lt 120 -and -not $win; $t++) { Start-Sleep -Milliseconds 250; $win = $A::RootElement.FindFirst($Scope::Children, (Cond $A::NameProperty 'Pickers')) }
if (-not $win) { "FAIL no Pickers window"; exit 1 }
$processId = $win.Current.ProcessId

Add-Type @"
using System; using System.Runtime.InteropServices;
public static class WinDpi { [DllImport("user32.dll")] public static extern uint GetDpiForWindow(IntPtr hwnd); }
"@
$scale = [WinDpi]::GetDpiForWindow([IntPtr]$win.Current.NativeWindowHandle) / 96.0
function Near($a, $b) { if ([Math]::Abs($a - $b) -le 2) { 'near' } else { "$a vs $b" } }

# Opens a picker's flyout, selects the named item in each looping selector, and accepts.
function Pick($picker, $selections) {
  Invoke-Id (Find-Id $win $picker) 'FlyoutButton'
  Start-Sleep -Milliseconds 600
  foreach ($selector in $selections.Keys) {
    $entry = Find-Plain (Find-Id $win $selector) $selections[$selector]
    (Pattern $entry ([System.Windows.Automation.ScrollItemPattern])).ScrollIntoView()
    (Pattern $entry $SelectionItem).Select()
    Start-Sleep -Milliseconds 300
  }
  Invoke-Id $win 'AcceptButton'
}
function Picker-Text($picker) { Plain (Find-Id (Find-Id $win $picker) 'FlyoutButton').Current.Name }

# DatePicker: the composed date is shown, a day picked in the flyout reaches the state, and the
# application can change and clear the date.
Check 'DatePicker initial state' 'date=2026-3-14' { Text $win 'dateEcho' }
Check 'DatePicker shows state' 'True' { [string]((Picker-Text 'date') -match '\b14\b.*\b2026\b') }
Invoke-Id $win 'setDate'
Check 'state sets DatePicker echo' 'date=2027-7-4' { Text $win 'dateEcho' }
Check 'state sets DatePicker' 'True' { [string]((Picker-Text 'date') -match '\b4\b.*\b2027\b') }
Pick 'date' @{ DayLoopingSelector = '20' }
Check 'DatePicker user pick reaches state' 'date=2027-7-20' { Text $win 'dateEcho' }
Check 'DatePicker shows picked date' 'True' { [string]((Picker-Text 'date') -match '\b20\b.*\b2027\b') }
Invoke-Id $win 'clearDate'
Check 'state clears DatePicker echo' 'date=none' { Text $win 'dateEcho' }
Check 'state clears DatePicker' 'True' { [string]((Picker-Text 'date') -notmatch '\d') }

# TimePicker (24-hour clock, 5-minute steps).
Check 'TimePicker initial state' 'time=09:30' { Text $win 'timeEcho' }
Check 'TimePicker shows state' 'True' { [string]((Picker-Text 'time') -match '\b0?9:30\b') }
Invoke-Id $win 'setTime'
Check 'state sets TimePicker echo' 'time=18:45' { Text $win 'timeEcho' }
Check 'state sets TimePicker' 'True' { [string]((Picker-Text 'time') -match '\b18:45\b') }
Pick 'time' @{ HourLoopingSelector = '14' }
Check 'TimePicker user pick reaches state' 'time=14:45' { Text $win 'timeEcho' }
Invoke-Id $win 'clearTime'
Check 'state clears TimePicker echo' 'time=none' { Text $win 'timeEcho' }
Check 'state clears TimePicker' 'True' { [string]((Picker-Text 'time') -notmatch '\d') }

# CalendarDatePicker: placeholder while null, the application's date, and a day picked in the calendar.
$calDate = Find-Id $win 'calDate'
function CalDate-Text() { Plain (Text $calDate 'DateText') }
Check 'CalendarDatePicker initial state' 'calDate=none' { Text $win 'calDateEcho' }
Check 'CalendarDatePicker placeholder' 'Pick a date' { CalDate-Text }
Invoke-Id $win 'setCalDate'
Check 'state sets CalendarDatePicker echo' 'calDate=2026-2-20' { Text $win 'calDateEcho' }
Check 'state sets CalendarDatePicker' 'True' { [string]((CalDate-Text) -match '\b20\b' -and (CalDate-Text) -match '\b2026\b') }
(Pattern $calDate $Invoke).Invoke()
Start-Sleep -Milliseconds 800
$popupCalendar = Find-Id $win 'CalendarView'
$day15 = if ($popupCalendar) { Find-Plain $popupCalendar '15' } else { $null }
if ($day15) { (Pattern $day15 $SelectionItem).Select() }
Check 'CalendarDatePicker user pick reaches state' 'calDate=2026-2-15' { Text $win 'calDateEcho' }
Check 'CalendarDatePicker shows picked date' 'True' { [string]((CalDate-Text) -match '\b15\b') }
Check 'CalendarDatePicker flyout closes' 'closed' { if (Find-Id $win 'CalendarView') { 'open' } else { 'closed' } }
Invoke-Id $win 'clearCalDate'
Check 'state clears CalendarDatePicker echo' 'calDate=none' { Text $win 'calDateEcho' }
Check 'state clears CalendarDatePicker' 'Pick a date' { CalDate-Text }

# CalendarView: the composed selection is shown, a selected day reaches the state, and the
# application can move the selection.
$calendar = Find-Id $win 'calendar'
function Day-Selected($name) { [string](Pattern (Find-Plain $calendar $name) $SelectionItem).Current.IsSelected }
Check 'CalendarView initial state' 'selected=2026-5-10' { Text $win 'calendarEcho' }
Check 'CalendarView shows state' 'True' { Day-Selected '10' }
(Pattern (Find-Plain $calendar '18') $SelectionItem).Select()
Check 'CalendarView user selection reaches state' 'selected=2026-5-18' { Text $win 'calendarEcho' }
Check 'CalendarView single selection' 'False' { Day-Selected '10' }
Invoke-Id $win 'setCalendar'
Check 'state sets CalendarView echo' 'selected=2026-5-25' { Text $win 'calendarEcho' }
Check 'state sets CalendarView' 'True' { Day-Selected '25' }
Check 'state deselects previous day' 'False' { Day-Selected '18' }

# InfoBadge and PersonPicture follow the state.
Check 'InfoBadge value' '3' { Text (Find-Id $win 'badgeValue') 'ValueTextBlock' }
Check 'InfoBadge icon has no number' 'none' { if (Find-Id (Find-Id $win 'badgeIcon') 'ValueTextBlock') { 'number' } else { 'none' } }
Check 'PersonPicture initials from name' 'AL' { (Find-Raw (Find-Raw $win 'person') 'InitialsTextBlock').Current.Name }
Check 'PersonPicture badge' '3' { (Find-Raw (Find-Raw $win 'person') 'BadgeNumberTextBlock').Current.Name }
Check 'PersonPicture explicit initials' 'BS' { (Find-Raw (Find-Raw $win 'personInitials') 'InitialsTextBlock').Current.Name }
Invoke-Id $win 'addCount'
Check 'state updates InfoBadge' '4' { Text (Find-Id $win 'badgeValue') 'ValueTextBlock' }
Check 'state updates PersonPicture badge' '4' { (Find-Raw (Find-Raw $win 'person') 'BadgeNumberTextBlock').Current.Name }

# PipsPager: the next button and a pip reach the state; the application's page reaches the pager,
# shown by the previous button stepping back from it.
$pips = Find-Id $win 'pips'
Check 'PipsPager initial state' 'page=0' { Text $win 'pageEcho' }
Invoke-Id $pips 'NextPageButton'
Check 'PipsPager next button' 'page=1' { Text $win 'pageEcho' }
$pip3 = $pips.FindAll($Scope::Descendants, (Cond $A::ControlTypeProperty ([System.Windows.Automation.ControlType]::Button))) | Where-Object { $_.Current.Name -match '\b3$' } | Select-Object -First 1
(Pattern $pip3 $Invoke).Invoke()
Check 'PipsPager pip' 'page=2' { Text $win 'pageEcho' }
Invoke-Id $win 'lastPage'
Check 'state sets PipsPager echo' 'page=4' { Text $win 'pageEcho' }
Start-Sleep -Milliseconds 300
Invoke-Id $pips 'PreviousPageButton'
Check 'PipsPager steps back from the state page' 'page=3' { Text $win 'pageEcho' }

# Viewbox scales its child to its height; Canvas places its child at the composed offset.
Check 'Viewbox scales child' 'near' { Near (Find-Id $win 'viewboxText').Current.BoundingRectangle.Height (60 * $scale) }
function Marker-Offset() { (Find-Id $win 'marker').Current.BoundingRectangle.X - (Find-Id $win 'origin').Current.BoundingRectangle.X }
function Marker-Top() { (Find-Id $win 'marker').Current.BoundingRectangle.Y - (Find-Id $win 'origin').Current.BoundingRectangle.Y }
Check 'Canvas left' 'near' { Near (Marker-Offset) (10 * $scale) }
Check 'Canvas top' 'near' { Near (Marker-Top) (20 * $scale) }
Invoke-Id $win 'moveRight'
Check 'state moves Canvas child' 'near' { Near (Marker-Offset) (60 * $scale) }

# RelativePanel: the title is at the panel's top-left and the action button at its bottom-right; the
# body sits below the title, and beside it once the state changes its constraints.
function Rect($id) { (Find-Id $win $id).Current.BoundingRectangle }
Check 'RelativePanel action at bottom-right (width)' 'near' { Near ((Rect 'relAction').Right - (Rect 'relTitle').X) (360 * $scale) }
Check 'RelativePanel action at bottom-right (height)' 'near' { Near ((Rect 'relAction').Bottom - (Rect 'relTitle').Y) (120 * $scale) }
Check 'RelativePanel body below title' 'below' { if ((Rect 'relBody').Y -ge (Rect 'relTitle').Bottom - 1) { 'below' } else { 'not below' } }
Check 'RelativePanel body aligned left with title' 'near' { Near (Rect 'relBody').X (Rect 'relTitle').X }
Invoke-Id $win 'relAction'
Check 'state changes RelativePanel constraints' 'body=beside' { Text $win 'relEcho' }
Check 'RelativePanel body right of title' 'beside' { if ((Rect 'relBody').X -ge (Rect 'relTitle').Right - 1) { 'beside' } else { 'not beside' } }
Check 'RelativePanel body aligned top with title' 'near' { Near (Rect 'relBody').Y (Rect 'relTitle').Y }
Invoke-Id $win 'relAction'
Check 'RelativePanel body below title again' 'below' { if ((Rect 'relBody').Y -ge (Rect 'relTitle').Bottom - 1) { 'below' } else { 'not below' } }

# SplitView: the application opens the pane, and light dismiss closes it and reaches the state.
Check 'SplitView initial state' 'pane=closed' { Text $win 'paneEcho' }
Check 'SplitView pane hidden' 'hidden' { if (Find-Id $win 'paneText') { 'shown' } else { 'hidden' } }
Invoke-Id $win 'openPane'
Check 'state opens SplitView echo' 'pane=open' { Text $win 'paneEcho' }
Check 'state opens SplitView pane' 'Pane content' { Text $win 'paneText' }
Invoke-Id $win 'LightDismiss'
Check 'SplitView light dismiss reaches state' 'pane=closed' { Text $win 'paneEcho' }
Start-Sleep -Milliseconds 600
Check 'SplitView pane stays closed' 'hidden' { if (Find-Id $win 'paneText') { 'shown' } else { 'hidden' } }
Invoke-Id $win 'openPane'
Check 'state reopens SplitView' 'Pane content' { Text $win 'paneText' }

(Pattern $win ([System.Windows.Automation.WindowPattern])).Close()
Check 'application exits' 'exited' { if (Get-Process -Id $processId -ErrorAction SilentlyContinue) { 'running' } else { 'exited' } }

if ($failures -eq 0) { "ALL PASS" } else { "FAILURES $failures"; exit 1 }
