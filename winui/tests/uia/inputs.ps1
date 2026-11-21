# UI Automation test for the inputs sample: ToggleButton, RepeatButton, SplitButton, ToggleSplitButton,
# RatingControl, AutoSuggestBox, RichEditBox and ColorPicker. Start the sample first.
# Prints PASS/FAIL per check and exits 1 if any check fails.
Add-Type -AssemblyName UIAutomationClient,UIAutomationTypes
$A = [System.Windows.Automation.AutomationElement]
$Scope = [System.Windows.Automation.TreeScope]
$failures = 0

function Cond($property, $value) { New-Object System.Windows.Automation.PropertyCondition($property, $value) }
function Pattern($element, $pattern) { $element.GetCurrentPattern($pattern::Pattern) }
function Find-Id($root, $id) { $root.FindFirst($Scope::Descendants, (Cond $A::AutomationIdProperty $id)) }
function Text($root, $id) { (Find-Id $root $id).Current.Name }
function Invoke-Id($root, $id) { (Pattern (Find-Id $root $id) ([System.Windows.Automation.InvokePattern])).Invoke() }
function Toggle-Id($root, $id) { (Pattern (Find-Id $root $id) ([System.Windows.Automation.TogglePattern])).Toggle() }
function ToggleState($root, $id) { [string](Pattern (Find-Id $root $id) ([System.Windows.Automation.TogglePattern])).Current.ToggleState }
function RangeValue($element) { [string](Pattern $element ([System.Windows.Automation.RangeValuePattern])).Current.Value }
function Value($element) { (Pattern $element ([System.Windows.Automation.ValuePattern])).Current.Value }
function Set-Value($element, $value) { (Pattern $element ([System.Windows.Automation.ValuePattern])).SetValue($value) }
# Parts inside a TextBox or RichEditBox template are only in the raw view.
$Raw = [System.Windows.Automation.TreeWalker]::RawViewWalker
function Find-Raw($root, $id) {
  $c = $Raw.GetFirstChild($root)
  while ($c) {
    if ($c.Current.AutomationId -eq $id) { return $c }
    $found = Find-Raw $c $id
    if ($found) { return $found }
    $c = $Raw.GetNextSibling($c)
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
for ($t = 0; $t -lt 120 -and -not $win; $t++) { Start-Sleep -Milliseconds 250; $win = $A::RootElement.FindFirst($Scope::Children, (Cond $A::NameProperty 'Inputs')) }
if (-not $win) { "FAIL no Inputs window"; exit 1 }
$processId = $win.Current.ProcessId

# Menus and suggestion lists open in popups: in the window or in top-level windows of the process.
# Only the process's own top-level windows are searched; a search below the desktop root would walk
# every application on the desktop.
function Popup-Item($name) {
  for ($t = 0; $t -lt 20; $t++) {
    foreach ($top in $A::RootElement.FindAll($Scope::Children, (Cond $A::ProcessIdProperty $processId))) {
      $m = $top.FindFirst($Scope::Descendants, (Cond $A::NameProperty $name))
      if ($m) { return $m }
    }
    Start-Sleep -Milliseconds 150
  }
  return $null
}
# A menu that is still closing ignores Expand, so retry until the wanted entry shows up.
function Open-Menu($id, $name) {
  $menu = Pattern (Find-Id $win $id) ([System.Windows.Automation.ExpandCollapsePattern])
  for ($try = 0; $try -lt 5; $try++) {
    try { $menu.Expand() } catch {}
    $m = Popup-Item $name
    if ($m) { return $m }
  }
  return $null
}

# ToggleButton: user toggles reach the state, state changes reach the button, rejected toggles are undone.
Check 'ToggleButton initial' 'bold=no' { Text $win 'boldEcho' }
Toggle-Id $win 'bold'
Check 'ToggleButton toggle reaches state' 'bold=yes' { Text $win 'boldEcho' }
Check 'ToggleButton shows checked' 'On' { ToggleState $win 'bold' }
Invoke-Id $win 'boldFromApp'
Check 'state unchecks ToggleButton' 'Off' { ToggleState $win 'bold' }
Check 'ToggleButton echo after state change' 'bold=no' { Text $win 'boldEcho' }
Toggle-Id $win 'locked'
Check 'ToggleButton rejected toggle restored' 'Off' { ToggleState $win 'locked' }

# RepeatButton: Invoke clicks once.
Invoke-Id $win 'repeat'
Check 'RepeatButton click' 'repeats=1' { Text $win 'repeatEcho' }
Invoke-Id $win 'repeat'
Check 'RepeatButton second click' 'repeats=2' { Text $win 'repeatEcho' }

# SplitButton: the main part and the menu.
Invoke-Id $win 'split'
Check 'SplitButton main part' 'action=paste' { Text $win 'splitEcho' }
$item = Open-Menu 'split' 'Paste as image'
if ($item) { (Pattern $item ([System.Windows.Automation.InvokePattern])).Invoke() }
Check 'SplitButton menu item' 'action=paste:image' { Text $win 'splitEcho' }
Check 'SplitButton menu closes' 'Collapsed' { [string](Pattern (Find-Id $win 'split') ([System.Windows.Automation.ExpandCollapsePattern])).Current.ExpandCollapseState }

# ToggleSplitButton: controlled checked state both ways, and the menu.
Toggle-Id $win 'list'
Check 'ToggleSplitButton toggle reaches state' 'list=yes style=bullets' { Text $win 'listEcho' }
Check 'ToggleSplitButton shows checked' 'On' { ToggleState $win 'list' }
Invoke-Id $win 'listFromApp'
Check 'state unchecks ToggleSplitButton' 'Off' { ToggleState $win 'list' }
Check 'ToggleSplitButton echo after state change' 'list=no style=bullets' { Text $win 'listEcho' }
$item = Open-Menu 'list' 'Numbers'
if ($item) { (Pattern $item ([System.Windows.Automation.InvokePattern])).Invoke() }
Check 'ToggleSplitButton menu item' 'list=yes style=numbers' { Text $win 'listEcho' }
Check 'menu item checks ToggleSplitButton through state' 'On' { ToggleState $win 'list' }

# RatingControl: user rating, state rating, clearing.
$rating = Find-Id $win 'rating'
Check 'RatingControl initial' 'rating=none' { Text $win 'ratingEcho' }
(Pattern $rating ([System.Windows.Automation.RangeValuePattern])).SetValue(3)
Check 'RatingControl user rating reaches state' 'rating=3' { Text $win 'ratingEcho' }
Check 'read-only RatingControl follows state' '3' { RangeValue (Find-Id $win 'ratingReadOnly') }
Invoke-Id $win 'rateFour'
Check 'state rates RatingControl' '4' { RangeValue $rating }
Check 'RatingControl echo after state change' 'rating=4' { Text $win 'ratingEcho' }
Invoke-Id $win 'clearRating'
Check 'state clears RatingControl' 'rating=none' { Text $win 'ratingEcho' }
Check 'RatingControl cleared' '0' { RangeValue $rating }
(Pattern $rating ([System.Windows.Automation.RangeValuePattern])).SetValue(2)
Check 'RatingControl rating after clear' 'rating=2' { Text $win 'ratingEcho' }

# AutoSuggestBox: the text box is the template's TextBox part.
$suggest = Find-Id $win 'suggest'
$suggestText = Find-Id $suggest 'TextBox'
Set-Value $suggestText 'Ap'
Check 'AutoSuggestBox typing reaches state' 'query=Ap chosen= submitted=' { Text $win 'suggestEcho' }
Set-Value $suggestText 'Ap1'
Check 'AutoSuggestBox rejected edit restored' 'Ap' { Value $suggestText }
Check 'AutoSuggestBox rejected edit keeps state' 'query=Ap chosen= submitted=' { Text $win 'suggestEcho' }
Invoke-Id $win 'suggestFromApp'
Check 'state sets AutoSuggestBox text' 'Cherry' { Value $suggestText }
Check 'AutoSuggestBox echo after state change' 'query=Cherry chosen= submitted=' { Text $win 'suggestEcho' }
(Pattern (Find-Raw $suggest 'QueryButton') ([System.Windows.Automation.InvokePattern])).Invoke()
Check 'AutoSuggestBox query submitted' 'query=Cherry chosen= submitted=Cherry' { Text $win 'suggestEcho' }
# Suggestions are listed while the box has focus. Invoking an entry submits it; WinUI raises
# SuggestionChosen for the click only when the entry is selected first, as a mouse click does.
$suggestText.SetFocus()
Set-Value $suggestText 'Ap'
$apricot = Popup-Item 'Apricot'
if ($apricot) {
  (Pattern $apricot ([System.Windows.Automation.SelectionItemPattern])).Select()
  (Pattern $apricot ([System.Windows.Automation.InvokePattern])).Invoke()
}
Check 'AutoSuggestBox suggestion chosen and submitted' 'query=Apricot chosen=Apricot submitted=suggestion:Apricot' { Text $win 'suggestEcho' }
Check 'AutoSuggestBox shows chosen suggestion' 'Apricot' { Value $suggestText }

# RichEditBox: uncontrolled; edits are reported as plain text.
Check 'RichEditBox initial text' 'rich=Hello' { Text $win 'richEcho' }
# The RichEditBox peer has no Value pattern; the text control inside it does.
$richText = $Raw.GetFirstChild((Find-Id $win 'rich'))
Set-Value $richText 'World'
Check 'RichEditBox edit reported' 'rich=World' { Text $win 'richEcho' }

# ColorPicker: state color reaches the picker; a user change through its value slider reaches the state.
# ColorPicker has no automation peer, so its template parts appear directly in the window.
Check 'ColorPicker initial' 'color=#FFFF0000' { Text $win 'colorEcho' }
Check 'ColorPicker shows initial color' '#FF0000' { Value (Find-Id $win 'HexTextBox') }
Invoke-Id $win 'blueFromApp'
Check 'state sets ColorPicker color' '#0000FF' { Value (Find-Id $win 'HexTextBox') }
Check 'ColorPicker echo after state change' 'color=#FF0000FF' { Text $win 'colorEcho' }
(Pattern (Find-Id $win 'ThirdDimensionSlider') ([System.Windows.Automation.RangeValuePattern])).SetValue(0)
Check 'ColorPicker user change reaches state' 'color=#FF000000' { Text $win 'colorEcho' }

(Pattern $win ([System.Windows.Automation.WindowPattern])).Close()
Check 'application exits' 'exited' { if (Get-Process -Id $processId -ErrorAction SilentlyContinue) { 'running' } else { 'exited' } }

if ($failures -eq 0) { "ALL PASS" } else { "FAILURES $failures"; exit 1 }
