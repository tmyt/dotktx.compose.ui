# UI Automation test for the commands sample. Start the sample first; this script finds its window,
# drives the controls through automation patterns and closes the application.
# Prints PASS/FAIL per check and exits 1 if any check fails.
Add-Type -AssemblyName UIAutomationClient,UIAutomationTypes
$A = [System.Windows.Automation.AutomationElement]
$Scope = [System.Windows.Automation.TreeScope]
$Invoke = [System.Windows.Automation.InvokePattern]
$Toggle = [System.Windows.Automation.TogglePattern]
$ExpandCollapse = [System.Windows.Automation.ExpandCollapsePattern]
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

# A search can miss an element while WinUI rebuilds part of the tree (for example while a popup
# closes), so lookups retry briefly.
function Find-Id($root, $id) {
  for ($t = 0; $t -lt 10; $t++) {
    $e = $root.FindFirst($Scope::Descendants, (Cond $A::AutomationIdProperty $id))
    if ($e) { return $e }
    Start-Sleep -Milliseconds 150
  }
  return $null
}

# Waits until $probe returns $expected (UI updates land after a frame), then records the result.
function Check($label, $expected, [scriptblock]$probe, $tries = 20) {
  $actual = $null
  for ($t = 0; $t -lt $tries; $t++) {
    try { $actual = & $probe } catch { $actual = "ERROR: $($_.Exception.Message)" }
    if ($actual -eq $expected) { break }
    Start-Sleep -Milliseconds 150
  }
  if ($actual -eq $expected) { "PASS $label" } else { $script:failures++; "FAIL $label`n     expected [$expected]`n     actual   [$actual]" }
}

function Text($root, $id) { (Find-Id $root $id).Current.Name }

$win = Find-Window 'Commands'
if (-not $win) { "FAIL no Commands window"; exit 1 }
$processId = $win.Current.ProcessId

function Invoke-Id($id) { (Pattern (Find-Id $win $id) $Invoke).Invoke() }

# Popups (menus, the overflow, flyouts, tips) are not necessarily under the window element: they can
# be top-level windows of their own. Search the window first, then the process's other top-level windows.
function In-Process($property, $value) {
  $condition = Cond $property $value
  $found = $win.FindFirst($Scope::Descendants, $condition)
  if ($found) { return $found }
  foreach ($top in $A::RootElement.FindAll($Scope::Children, (Cond $A::ProcessIdProperty $processId))) {
    if ($top.Current.Name -eq 'Commands') { continue }
    $found = $top.FindFirst($Scope::Subtree, $condition)
    if ($found) { return $found }
  }
  return $null
}
function Wait-Named($name) {
  for ($t = 0; $t -lt 20; $t++) {
    $m = In-Process $A::NameProperty $name
    if ($m) { return $m }
    Start-Sleep -Milliseconds 150
  }
  return $null
}
function Presence($property, $value) { if (In-Process $property $value) { 'present' } else { 'absent' } }
function ToggleState($element) { [string](Pattern $element $Toggle).Current.ToggleState }

# CommandBar: primary commands, controlled toggles (accepted and rejected), the content slot.
$bar = Find-Id $win 'commandBar'
function Bar-Item($name) { $bar.FindFirst($Scope::Descendants, (Cond $A::NameProperty $name)) }
Check 'CommandBar content slot' 'Document.txt' { Text $win 'barContent' }
(Pattern (Bar-Item 'Save') $Invoke).Invoke()
Check 'CommandBar button' 'command=save bold=no wrap=no rejected=0 open=no' { Text $win 'commandEcho' }
(Pattern (Bar-Item 'Bold') $Toggle).Toggle()
Check 'CommandBar toggle reaches state' 'command=save bold=yes wrap=no rejected=0 open=no' { Text $win 'commandEcho' }
Check 'CommandBar toggle checked' 'On' { ToggleState (Bar-Item 'Bold') }
Invoke-Id 'toggleBold'
Check 'state unchecks CommandBar toggle' 'Off' { ToggleState (Bar-Item 'Bold') }
(Pattern (Bar-Item 'Pin') $Toggle).Toggle()
Check 'CommandBar rejected toggle reported' 'command=save bold=no wrap=no rejected=1 open=no' { Text $win 'commandEcho' }
Check 'CommandBar rejected toggle restored' 'Off' { ToggleState (Bar-Item 'Pin') }

# CommandBar overflow: opened by the user through ExpandCollapse and by the application through isOpen.
$overflow = $null
try { $overflow = Pattern $bar $ExpandCollapse } catch {}
if (-not $overflow) {
  $more = Find-Id $bar 'MoreButton'
  if ($more) { try { $overflow = Pattern $more $ExpandCollapse } catch {} }
}
if (-not $overflow) { $failures++; "FAIL CommandBar has no ExpandCollapse pattern" }
else {
  $overflow.Expand()
  Check 'user opens overflow' 'command=save bold=no wrap=no rejected=1 open=yes' { Text $win 'commandEcho' }
  $settings = Wait-Named 'Settings'
  if ($settings) { (Pattern $settings $Invoke).Invoke() }
  Check 'secondary command' 'command=settings bold=no wrap=no rejected=1 open=no' { Text $win 'commandEcho' }
  Check 'overflow closes after secondary command' 'Collapsed' { [string]$overflow.Current.ExpandCollapseState }
  Invoke-Id 'openBar'
  Check 'state opens overflow' 'Expanded' { [string]$overflow.Current.ExpandCollapseState }
  $wrapItem = Wait-Named 'Wrap'
  if ($wrapItem) { (Pattern $wrapItem $Toggle).Toggle() }
  Check 'secondary toggle' 'wrap=yes' { if ((Text $win 'commandEcho') -match 'wrap=(\w+)') { "wrap=$($Matches[1])" } }
  Invoke-Id 'openBar'
  Check 'overflow open for user close' 'Expanded' { [string]$overflow.Current.ExpandCollapseState }
  $overflow.Collapse()
  Check 'user closes overflow' 'open=no' { if ((Text $win 'commandEcho') -match 'open=(\w+)') { "open=$($Matches[1])" } }
  Invoke-Id 'openBar'
  Check 'overflow open for state close' 'Expanded' { [string]$overflow.Current.ExpandCollapseState }
  Invoke-Id 'closeBar'
  Check 'state closes overflow' 'Collapsed' { [string]$overflow.Current.ExpandCollapseState }
}

# MenuBar: items, submenus and toggle entries built from MenuEntry lists.
$menuBar = Find-Id $win 'menuBar'
function Open-Menu($title, $item) {
  $top = $menuBar.FindFirst($Scope::Descendants, (Cond $A::NameProperty $title))
  # A menu that is still closing ignores Expand, so retry until the wanted entry shows up.
  for ($try = 0; $try -lt 5; $try++) {
    try { (Pattern $top $ExpandCollapse).Expand() } catch { try { (Pattern $top $Invoke).Invoke() } catch {} }
    $m = Wait-Named $item
    if ($m) { return $m }
  }
  return $null
}
(Pattern (Open-Menu 'File' 'New') $Invoke).Invoke()
Check 'MenuBar item' 'menu=new statusBar=no' { Text $win 'menuEcho' }
(Pattern (Open-Menu 'View' 'Status bar') $Toggle).Toggle()
Check 'MenuBar toggle entry' 'menu=new statusBar=yes' { Text $win 'menuEcho' }
Check 'MenuBar toggle reopens checked' 'On' { ToggleState (Open-Menu 'View' 'Status bar') }
(Pattern (Wait-Named 'Read only') $Toggle).Toggle()
Check 'MenuBar rejected toggle reported' 'menu=readonly:rejected statusBar=yes' { Text $win 'menuEcho' }
Check 'MenuBar rejected toggle reopens unchecked' 'Off' { ToggleState (Open-Menu 'View' 'Read only') }
Check 'MenuBar accepted toggle still checked' 'On' { ToggleState (Wait-Named 'Status bar') }
(Pattern (Open-Menu 'File' 'Export') $ExpandCollapse).Expand()
(Pattern (Wait-Named 'PDF') $Invoke).Invoke()
Check 'MenuBar submenu item' 'menu=export:pdf statusBar=yes' { Text $win 'menuEcho' }

# Flyout: shown while composed, content composed with the caller's state, closed by the application.
Check 'flyout absent initially' 'absent' { Presence $A::AutomationIdProperty 'flyoutText' }
Invoke-Id 'rename'
Check 'flyout opens' 'flyout=open name=draft dismissals=0' { Text $win 'flyoutEcho' }
Check 'flyout content visible' 'Rename the document' { (In-Process $A::AutomationIdProperty 'flyoutText').Current.Name }
(Pattern (In-Process $A::AutomationIdProperty 'flyoutName') ([System.Windows.Automation.ValuePattern])).SetValue('final')
Check 'flyout content updates state' 'flyout=open name=final dismissals=0' { Text $win 'flyoutEcho' }
(Pattern (In-Process $A::AutomationIdProperty 'flyoutDone') $Invoke).Invoke()
Check 'application closes flyout' 'flyout=closed name=final dismissals=0' { Text $win 'flyoutEcho' }
Check 'flyout content gone' 'absent' { Presence $A::AutomationIdProperty 'flyoutText' }
Invoke-Id 'rename'
Check 'flyout reopens' 'Rename the document' { (In-Process $A::AutomationIdProperty 'flyoutText').Current.Name }
(Pattern (In-Process $A::AutomationIdProperty 'flyoutDone') $Invoke).Invoke()
Check 'flyout closed again' 'absent' { Presence $A::AutomationIdProperty 'flyoutText' }

# TeachingTip: opened and closed by state; the close button reports to the application, and the tip
# opens again if the application keeps it open.
function Tip-Button($name) {
  $condition = New-Object System.Windows.Automation.AndCondition((Cond $A::ControlTypeProperty ([System.Windows.Automation.ControlType]::Button)), (Cond $A::NameProperty $name))
  for ($t = 0; $t -lt 20; $t++) {
    $b = $win.FindFirst($Scope::Descendants, $condition)
    if ($b) { return $b }
    Start-Sleep -Milliseconds 150
  }
  return $null
}
# The tip ignores its buttons until its opening animation has finished.
function Wait-TipOpened() { Start-Sleep -Milliseconds 700 }
Check 'tip closed initially' 'absent' { Presence $A::AutomationIdProperty 'tipContent' }
Invoke-Id 'showTip'
Check 'state opens tip' 'Tip content' { (In-Process $A::AutomationIdProperty 'tipContent').Current.Name }
Check 'tip title' 'Tip title' { (In-Process $A::NameProperty 'Tip title').Current.Name }
Wait-TipOpened
(Pattern (Tip-Button 'Do it') $Invoke).Invoke()
Check 'tip action button' 'tip=open actions=1 dismissals=0' { Text $win 'tipEcho' }
(Pattern (Tip-Button 'Got it') $Invoke).Invoke()
Check 'tip close button reaches state' 'tip=closed actions=1 dismissals=1' { Text $win 'tipEcho' }
Check 'tip closes' 'absent' { Presence $A::AutomationIdProperty 'tipContent' }
(Pattern (Find-Id $win 'keepTip') $Toggle).Toggle()
Invoke-Id 'showTip'
Check 'tip reopens' 'Tip content' { (In-Process $A::AutomationIdProperty 'tipContent').Current.Name }
Wait-TipOpened
(Pattern (Tip-Button 'Got it') $Invoke).Invoke()
Check 'refused close reported' 'tip=open actions=1 dismissals=2' { Text $win 'tipEcho' }
Start-Sleep -Milliseconds 800
Check 'refused close opens tip again' 'present' { Presence $A::AutomationIdProperty 'tipContent' }
Invoke-Id 'hideTip'
Check 'state closes tip' 'tip=closed actions=1 dismissals=2' { Text $win 'tipEcho' }
Check 'tip closed by state' 'absent' { Presence $A::AutomationIdProperty 'tipContent' }
(Pattern (Find-Id $win 'keepTip') $Toggle).Toggle()

# WebView2: the composed source is navigated to; navigations started by the page reach the state, and
# a rejected one sends the web view back to the composed source. The web content itself has no
# automation peers, so the checks read what the application shows.
function Navigated() { (Text $win 'navigatedEcho') -replace ' count=\d+', '' }
function NavigationCount() { if ((Text $win 'navigatedEcho') -match 'count=(\d+)') { [int]$Matches[1] } else { -1 } }
Check 'web initial source' 'source=A rejected=0' { Text $win 'sourceEcho' }
Check 'web initial page loaded' 'navigated=Page A:ok' { Navigated } 100
Invoke-Id 'pageB'
Check 'state navigates web view' 'navigated=Page B:ok' { Navigated } 60
Invoke-Id 'pageA'
Check 'state navigates back' 'navigated=Page A:ok' { Navigated } 60
Invoke-Id 'pageC'
Check 'page navigation reaches state' 'source=about:blank rejected=0' { Text $win 'sourceEcho' } 60
(Pattern (Find-Id $win 'lockSource') $Toggle).Toggle()
Invoke-Id 'pageC'
Check 'rejected page navigation reported' 'source=C rejected=1' { Text $win 'sourceEcho' } 60
$afterReject = NavigationCount
Check 'rejected page navigation returns to source' 'navigated=Page C:ok' { if ((NavigationCount) -gt $afterReject) { Navigated } else { 'waiting' } } 60
(Pattern (Find-Id $win 'lockSource') $Toggle).Toggle()
Check 'page navigation accepted again' 'about:blank' { if ((Text $win 'sourceEcho') -match 'source=(\S+)') { $Matches[1] } } 60

(Pattern $win ([System.Windows.Automation.WindowPattern])).Close()
Check 'application exits' 'exited' { if (Get-Process -Id $processId -ErrorAction SilentlyContinue) { 'running' } else { 'exited' } }

if ($failures -eq 0) { "ALL PASS" } else { "FAILURES $failures"; exit 1 }
