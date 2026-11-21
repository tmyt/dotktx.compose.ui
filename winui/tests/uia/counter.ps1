# UI Automation test for the counter sample. Start the sample first; this script clicks the button
# three times, closes the window and checks that the process exits.
Add-Type -AssemblyName UIAutomationClient,UIAutomationTypes
$A = [System.Windows.Automation.AutomationElement]
$win = $null
for ($t = 0; $t -lt 30 -and -not $win; $t++) {
  Start-Sleep -Seconds 1
  $win = $A::RootElement.FindFirst([System.Windows.Automation.TreeScope]::Children, (New-Object System.Windows.Automation.PropertyCondition($A::NameProperty, 'Counter')))
}
if (-not $win) { "no Counter window"; exit 1 }
$proc = Get-Process -Id $win.Current.ProcessId
"pid " + $proc.Id + " responding=" + $proc.Responding
$btn = $win.FindFirst([System.Windows.Automation.TreeScope]::Descendants, (New-Object System.Windows.Automation.PropertyCondition($A::ControlTypeProperty, [System.Windows.Automation.ControlType]::Button)))
"before: " + $btn.Current.Name
$inv = $btn.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern)
for ($i = 1; $i -le 3; $i++) { $inv.Invoke(); Start-Sleep -Milliseconds 400; "after click ${i}: " + $btn.Current.Name }
$win.GetCurrentPattern([System.Windows.Automation.WindowPattern]::Pattern).Close()
Start-Sleep -Seconds 3
if (Get-Process -Id $proc.Id -ErrorAction SilentlyContinue) { "still running after close" } else { "exited after close" }
