# UI Automation test for the shell sample: TitleBar, NavigationView, TabView and Expander.
# Start the sample first. Prints PASS/FAIL per check and exits 1 if any check fails.
Add-Type -AssemblyName UIAutomationClient,UIAutomationTypes
$A = [System.Windows.Automation.AutomationElement]
$Scope = [System.Windows.Automation.TreeScope]
$failures = 0

function Cond($property, $value) { New-Object System.Windows.Automation.PropertyCondition($property, $value) }
function Pattern($element, $pattern) { $element.GetCurrentPattern($pattern::Pattern) }
function Find-Id($root, $id) { $root.FindFirst($Scope::Descendants, (Cond $A::AutomationIdProperty $id)) }
function Find-Name($root, $name) { $root.FindFirst($Scope::Descendants, (Cond $A::NameProperty $name)) }
function Text($root, $id) { (Find-Id $root $id).Current.Name }
function Select-Name($root, $name) { (Pattern (Find-Name $root $name) ([System.Windows.Automation.SelectionItemPattern])).Select() }
function Invoke-Id($root, $id) { (Pattern (Find-Id $root $id) ([System.Windows.Automation.InvokePattern])).Invoke() }

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
for ($t = 0; $t -lt 120 -and -not $win; $t++) { Start-Sleep -Milliseconds 250; $win = $A::RootElement.FindFirst($Scope::Children, (Cond $A::NameProperty 'Shell')) }
if (-not $win) { "FAIL no Shell window"; exit 1 }
$processId = $win.Current.ProcessId

# TitleBar
Check 'TitleBar title' 'Shell' { Text $win 'PART_TitleText' }
Check 'TitleBar subtitle' 'Compose on WinUI' { Text $win 'PART_SubtitleText' }
Check 'initial state' 'page=home history=0 pane=open' { Text $win 'shellEcho' }

# NavigationView selection drives the page.
Select-Name $win 'Documents'
Check 'navigate to Documents' 'page=documents history=1 pane=open' { Text $win 'shellEcho' }

# TabView: the selected tab's content, adding and closing tabs.
Check 'first tab content' 'Editing Doc 1' { Text $win 'tabContent' }
Select-Name $win 'Doc 2'
Check 'select second tab' 'tabs=1,2 selected=2' { Text $win 'tabsEcho' }
Check 'second tab content' 'Editing Doc 2' { Text $win 'tabContent' }
(Pattern (Find-Id $win 'docText') ([System.Windows.Automation.ValuePattern])).SetValue('hello')
Select-Name $win 'Doc 1'
Check 'other tab has its own text' '' { (Pattern (Find-Id $win 'docText') ([System.Windows.Automation.ValuePattern])).Current.Value }
Select-Name $win 'Doc 2'
Check 'tab text kept' 'hello' { (Pattern (Find-Id $win 'docText') ([System.Windows.Automation.ValuePattern])).Current.Value }
Invoke-Id $win 'AddButton'
Check 'add tab' 'tabs=1,2,3 selected=3' { Text $win 'tabsEcho' }
Check 'added tab content' 'Editing Doc 3' { Text $win 'tabContent' }
$doc1 = Find-Name (Find-Id $win 'tabs') 'Doc 1'
(Pattern (Find-Id $doc1 'CloseButton') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'close tab' 'tabs=2,3 selected=3' { Text $win 'tabsEcho' }
Check 'content after close' 'Editing Doc 3' { Text $win 'tabContent' }

# TitleBar pane toggle controls the NavigationView pane.
Invoke-Id $win 'PART_PaneToggleButton'
Check 'pane toggle closes' 'page=documents history=1 pane=closed' { Text $win 'shellEcho' }
Invoke-Id $win 'PART_PaneToggleButton'
Check 'pane toggle opens' 'page=documents history=1 pane=open' { Text $win 'shellEcho' }

# Expander: user expansion and application-driven collapse.
Select-Name $win 'About'
Check 'navigate to About' 'page=about history=2 pane=open' { Text $win 'shellEcho' }
(Pattern (Find-Id $win 'details') ([System.Windows.Automation.ExpandCollapsePattern])).Expand()
Check 'Expander expands' 'expanded=yes' { Text $win 'detailsEcho' }
Check 'Expander content' 'Built with the Compose runtime on WinUI 3.' { Text $win 'detailsText' }
Invoke-Id $win 'toggleDetails'
Check 'state collapses Expander' 'Collapsed' { [string](Pattern (Find-Id $win 'details') ([System.Windows.Automation.ExpandCollapsePattern])).Current.ExpandCollapseState }
Check 'Expander echo' 'expanded=no' { Text $win 'detailsEcho' }

# TitleBar back button walks the history.
Invoke-Id $win 'PART_BackButton'
Check 'back to Documents' 'page=documents history=1 pane=open' { Text $win 'shellEcho' }
Check 'navigation selection follows back' 'True' { [string](Pattern (Find-Name $win 'Documents') ([System.Windows.Automation.SelectionItemPattern])).Current.IsSelected }
Invoke-Id $win 'PART_BackButton'
Check 'back to Home' 'page=home history=0 pane=open' { Text $win 'shellEcho' }

(Pattern $win ([System.Windows.Automation.WindowPattern])).Close()
Check 'application exits' 'exited' { if (Get-Process -Id $processId -ErrorAction SilentlyContinue) { 'running' } else { 'exited' } }

if ($failures -eq 0) { "ALL PASS" } else { "FAILURES $failures"; exit 1 }
