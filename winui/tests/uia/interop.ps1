# UI Automation test for the interop sample: a plain WinUI app with a ComposeHost.
# Start the sample first. Prints PASS/FAIL per check and exits 1 if any check fails.
Add-Type -AssemblyName UIAutomationClient,UIAutomationTypes
$A = [System.Windows.Automation.AutomationElement]
$Scope = [System.Windows.Automation.TreeScope]
$failures = 0

function Cond($property, $value) { New-Object System.Windows.Automation.PropertyCondition($property, $value) }
function Pattern($element, $pattern) { $element.GetCurrentPattern($pattern::Pattern) }
function Find-Id($root, $id) { $root.FindFirst($Scope::Descendants, (Cond $A::AutomationIdProperty $id)) }
function Text($root, $id) { (Find-Id $root $id).Current.Name }
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
for ($t = 0; $t -lt 120 -and -not $win; $t++) { Start-Sleep -Milliseconds 250; $win = $A::RootElement.FindFirst($Scope::Children, (Cond $A::NameProperty 'Interop')) }
if (-not $win) { "FAIL no Interop window"; exit 1 }
$processId = $win.Current.ProcessId

Check 'hosted content composed' 'Compose clicks: 0, native clicks: 0' { Text $win 'composeLabel' }
Invoke-Id $win 'composeButton'
Check 'hosted content recomposes' 'Compose clicks: 1, native clicks: 0' { Text $win 'composeLabel' }
Invoke-Id $win 'nativeButton'
Check 'native control still works' 'Native clicks: 1' { Text $win 'nativeLabel' }
Check 'state written by native code recomposes Compose' 'Compose clicks: 1, native clicks: 1' { Text $win 'composeLabel' }

Invoke-Id $win 'composeDialog'
$ok = $null
for ($t = 0; $t -lt 20 -and -not $ok; $t++) { Start-Sleep -Milliseconds 150; $ok = $win.FindFirst($Scope::Descendants, (New-Object System.Windows.Automation.AndCondition((Cond $A::ControlTypeProperty ([System.Windows.Automation.ControlType]::Button)), (Cond $A::NameProperty 'OK')))) }
if ($ok) { (Pattern $ok ([System.Windows.Automation.InvokePattern])).Invoke() }
Check 'ContentDialog from a ComposeHost' 'dialog=ok' { Text $win 'dialogEcho' }

(Pattern $win ([System.Windows.Automation.WindowPattern])).Close()
Check 'application exits' 'exited' { if (Get-Process -Id $processId -ErrorAction SilentlyContinue) { 'running' } else { 'exited' } }

if ($failures -eq 0) { "ALL PASS" } else { "FAILURES $failures"; exit 1 }
