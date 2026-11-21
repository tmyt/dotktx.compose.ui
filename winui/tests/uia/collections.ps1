# UI Automation test for the collections sample: ListView, GridView, TreeView, FlipView,
# BreadcrumbBar and SelectorBar. Start the sample first. Prints PASS/FAIL per check and exits 1 if
# any check fails.
Add-Type -AssemblyName UIAutomationClient,UIAutomationTypes
$A = [System.Windows.Automation.AutomationElement]
$Scope = [System.Windows.Automation.TreeScope]
$SelectionItem = [System.Windows.Automation.SelectionItemPattern]
$ExpandCollapse = [System.Windows.Automation.ExpandCollapsePattern]
$Invoke = [System.Windows.Automation.InvokePattern]
$Toggle = [System.Windows.Automation.TogglePattern]
$failures = 0
# Errors end the current action or probe; Act and Check report them on one line.
$ErrorActionPreference = 'Stop'

function Cond($property, $value) { New-Object System.Windows.Automation.PropertyCondition($property, $value) }
function Pattern($element, $pattern) { $element.GetCurrentPattern($pattern::Pattern) }
function Find-Id($root, $id) { $root.FindFirst($Scope::Descendants, (Cond $A::AutomationIdProperty $id)) }
function Find-Name($root, $name) { $root.FindFirst($Scope::Descendants, (Cond $A::NameProperty $name)) }
function Text($root, $id) { (Find-Id $root $id).Current.Name }
function Invoke-Id($root, $id) { (Pattern (Find-Id $root $id) $Invoke).Invoke() }
function Present($root, $id) { if (Find-Id $root $id) { 'present' } else { 'absent' } }

# The item (ListViewItem, TreeViewItem, …) that contains the composed element with AutomationId $id:
# the nearest ancestor that supports the SelectionItem pattern.
function Item-Of($root, $id) {
  $e = Find-Id $root $id
  $walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
  $p = $null
  while ($e -and -not $e.TryGetCurrentPattern($SelectionItem::Pattern, [ref]$p)) { $e = $walker.GetParent($e) }
  return $e
}
function Selected($root, $id) { [string](Pattern (Item-Of $root $id) $SelectionItem).Current.IsSelected }
function Select-Item($root, $id) { (Pattern (Item-Of $root $id) $SelectionItem).Select() }

function Check($label, $expected, [scriptblock]$probe) {
  $actual = $null
  for ($t = 0; $t -lt 20; $t++) {
    try { $actual = & $probe } catch { $actual = "ERROR: $($_.Exception.Message)" }
    if ($actual -eq $expected) { break }
    Start-Sleep -Milliseconds 150
  }
  if ($actual -eq $expected) { "PASS $label" } else { $script:failures++; "FAIL $label`n     expected [$expected]`n     actual   [$actual]" }
}

# Runs a user or application action; a failure is reported and the checks that follow show its effect.
function Act([scriptblock]$action) { try { & $action } catch { "NOTE action failed: $($_.Exception.Message)" } }

$win = $null
for ($t = 0; $t -lt 120 -and -not $win; $t++) { Start-Sleep -Milliseconds 250; $win = $A::RootElement.FindFirst($Scope::Children, (Cond $A::NameProperty 'Collections')) }
if (-not $win) { "FAIL no Collections window"; exit 1 }
$processId = $win.Current.ProcessId

# ListView: items are realized with their composed content, selection flows both ways, and keyed
# rows keep their remembered state when a row is inserted above them.
Check 'ListView item content realized' 'Banana' { Text $win 'fruit2' }
Check 'ListView item is a list item' 'ListItem' { (Item-Of $win 'fruit2').Current.ControlType.ProgrammaticName -replace 'ControlType.','' }
Act { Select-Item $win 'fruit3' }
Check 'ListView user selection reaches state' 'fruit=Cherry index=2 order=1,2,3,4 clicks=0' { Text $win 'fruitEcho' }
Act { Invoke-Id $win 'selectFirstFruit' }
Check 'ListView state selection reaches control' 'True' { Selected $win 'fruit1' }
Check 'ListView previous item deselected' 'False' { Selected $win 'fruit3' }
Act { (Pattern (Find-Id $win 'ripe2') $Toggle).Toggle() }
Check 'ListView row state set' 'On' { [string](Pattern (Find-Id $win 'ripe2') $Toggle).Current.ToggleState }
Act { Invoke-Id $win 'insertFruit' }
Check 'ListView keyed insert' 'Elderberry 5' { Text $win 'fruit5' }
Check 'ListView inserted row starts fresh' 'Off' { [string](Pattern (Find-Id $win 'ripe5') $Toggle).Current.ToggleState }
Check 'ListView row state kept after insert' 'On' { [string](Pattern (Find-Id $win 'ripe2') $Toggle).Current.ToggleState }
Check 'ListView neighbour state after insert' 'Off' { [string](Pattern (Find-Id $win 'ripe1') $Toggle).Current.ToggleState }
# The application's index stays 0, so the selection moves to the inserted row.
Check 'ListView controlled index after insert' 'True' { Selected $win 'fruit5' }
Check 'ListView echo after insert' 'fruit=Elderberry 5 index=0 order=5,1,2,3,4 clicks=0' { Text $win 'fruitEcho' }
Act { Invoke-Id $win 'reverseFruits' }
Check 'ListView keyed reorder on screen' '4,3,2,1,5' {
  $texts = (Find-Id $win 'fruits').FindAll($Scope::Descendants, (Cond $A::ControlTypeProperty ([System.Windows.Automation.ControlType]::Text)))
  @($texts | Where-Object { $_.Current.AutomationId -like 'fruit*' } | Sort-Object { $_.Current.BoundingRectangle.Y } | ForEach-Object { $_.Current.AutomationId -replace 'fruit','' }) -join ','
}
Check 'ListView echo after reorder' 'fruit=Damson index=0 order=4,3,2,1,5 clicks=0' { Text $win 'fruitEcho' }
Check 'ListView item named by its content' 'Apple' { (Item-Of $win 'fruit1').Current.Name }
Act { (Pattern (Item-Of $win 'fruit3') $Invoke).Invoke() }
Check 'ListView item click' 'clicks=1' { (Text $win 'fruitEcho') -replace '.* (clicks=\d+)$', '$1' }

# ListView with multiple selection.
Check 'multiple initial selection' 'True' { Selected $win 'letterBravo' }
Act { (Pattern (Item-Of $win 'letterDelta') $SelectionItem).AddToSelection() }
Check 'multiple add reaches state' 'letters=1,3' { Text $win 'lettersEcho' }
Act { (Pattern (Item-Of $win 'letterBravo') $SelectionItem).RemoveFromSelection() }
Check 'multiple remove reaches state' 'letters=3' { Text $win 'lettersEcho' }
Act { Invoke-Id $win 'selectEnds' }
Check 'multiple state reaches control (first)' 'True' { Selected $win 'letterAlpha' }
Check 'multiple state reaches control (last)' 'True' { Selected $win 'letterEcho' }
Check 'multiple state deselects' 'False' { Selected $win 'letterDelta' }
Check 'multiple echo' 'letters=0,4' { Text $win 'lettersEcho' }

# GridView.
Check 'GridView item content realized' 'Cyan' { Text $win 'colorCyan' }
Act { Select-Item $win 'colorBlue' }
Check 'GridView user selection reaches state' 'color=Blue' { Text $win 'colorEcho' }
Act { Invoke-Id $win 'selectYellow' }
Check 'GridView state selection reaches control' 'True' { Selected $win 'colorYellow' }
Check 'GridView previous item deselected' 'False' { Selected $win 'colorBlue' }

# TreeView: controlled expansion (ExpandCollapse) and selection (SelectionItem).
Check 'TreeView item content realized' '[Documents]' { Text $win 'folderDocuments' }
Check 'TreeView initially expanded child' '[Work]' { Text $win 'folderWork' }
Check 'TreeView collapsed child not realized' 'absent' { Present $win 'folderHoliday' }
Act { (Pattern (Item-Of $win 'folderWork') $ExpandCollapse).Expand() }
Check 'TreeView user expand reaches state' 'folder=none expanded=Documents,Work' { Text $win 'folderEcho' }
Check 'TreeView expanded children realized' '[Report]' { Text $win 'folderReport' }
Act { Select-Item $win 'folderReport' }
Check 'TreeView user selection reaches state' 'folder=Report expanded=Documents,Work' { Text $win 'folderEcho' }
Act { (Pattern (Item-Of $win 'folderDocuments') $ExpandCollapse).Collapse() }
Check 'TreeView user collapse reaches state' 'folder=Report expanded=Work' { Text $win 'folderEcho' }
Check 'TreeView collapsed children removed' 'absent' { Present $win 'folderWork' }
Act { Invoke-Id $win 'expandAll' }
Check 'TreeView state expands' 'Expanded' { [string](Pattern (Item-Of $win 'folderPictures') $ExpandCollapse).Current.ExpandCollapseState }
Check 'TreeView state expansion realizes children' '[Holiday]' { Text $win 'folderHoliday' }
Check 'TreeView expand echo' 'folder=Report expanded=Documents,Pictures,Work' { Text $win 'folderEcho' }
Act { Invoke-Id $win 'selectPersonal' }
Check 'TreeView state selection reaches control' 'True' { Selected $win 'folderPersonal' }
Check 'TreeView previous selection cleared' 'False' { Selected $win 'folderReport' }
Act { Invoke-Id $win 'collapseAll' }
Check 'TreeView state collapses' 'Collapsed' { [string](Pattern (Item-Of $win 'folderDocuments') $ExpandCollapse).Current.ExpandCollapseState }
Check 'TreeView collapse echo' 'folder=Personal expanded=' { Text $win 'folderEcho' }
Check 'TreeView item named by its content' '[Documents]' { (Item-Of $win 'folderDocuments').Current.Name }

# FlipView.
# Only the selected item is in the control view; the next one is reached through ItemContainer.
function Flip-Item($index) {
  $container = Pattern (Find-Id $win 'pages') ([System.Windows.Automation.ItemContainerPattern])
  $item = $null
  for ($i = 0; $i -le $index; $i++) { $item = $container.FindItemByProperty($item, $null, $null) }
  $virtual = $null
  if ($item.TryGetCurrentPattern([System.Windows.Automation.VirtualizedItemPattern]::Pattern, [ref]$virtual)) { $virtual.Realize() }
  return $item
}
function Flip-Selection() { (Pattern (Find-Id $win 'pages') ([System.Windows.Automation.SelectionPattern])).Current.GetSelection()[0].Current.Name }
Check 'FlipView item content realized' 'Page 1' { Text $win 'page1' }
Check 'FlipView item named by its content' 'Page 1' { Flip-Selection }
Act { (Pattern (Flip-Item 1) $SelectionItem).Select() }
Check 'FlipView user selection reaches state' 'page=1' { Text $win 'pageEcho' }
Act { Invoke-Id $win 'lastPage' }
Check 'FlipView echo' 'page=3' { Text $win 'pageEcho' }
Check 'FlipView state selection reaches control' 'Page 4' { Flip-Selection }

# BreadcrumbBar.
$crumbs = Find-Id $win 'crumbs'
Check 'BreadcrumbBar items' 'Work' { (Find-Name $crumbs 'Work').Current.Name }
Act { (Pattern (Find-Name $crumbs 'Documents') $Invoke).Invoke() }
Check 'BreadcrumbBar click' 'path=Home/Documents' { Text $win 'pathEcho' }
Check 'BreadcrumbBar follows state' 'absent' { if (Find-Name $crumbs 'Work') { 'present' } else { 'absent' } }
Act { Invoke-Id $win 'deeper' }
Check 'BreadcrumbBar new item' 'Folder 2' { (Find-Name $crumbs 'Folder 2').Current.Name }
Check 'BreadcrumbBar echo' 'path=Home/Documents/Folder 2' { Text $win 'pathEcho' }

# SelectorBar.
# SelectorBar has no automation peer of its own; its items are found by class and name.
function Selector-Item($name) {
  $win.FindFirst($Scope::Descendants, (New-Object System.Windows.Automation.AndCondition((Cond $A::ClassNameProperty 'Microsoft.UI.Xaml.Controls.SelectorBarItem'), (Cond $A::NameProperty $name))))
}
Check 'SelectorBar initial selection' 'True' { [string](Pattern (Selector-Item 'Recent') $SelectionItem).Current.IsSelected }
Act { (Pattern (Selector-Item 'Shared') $SelectionItem).Select() }
Check 'SelectorBar user selection reaches state' 'view=Shared' { Text $win 'viewEcho' }
Act { Invoke-Id $win 'selectFavorites' }
Check 'SelectorBar state selection reaches control' 'True' { [string](Pattern (Selector-Item 'Favorites') $SelectionItem).Current.IsSelected }
Check 'SelectorBar previous item deselected' 'False' { [string](Pattern (Selector-Item 'Shared') $SelectionItem).Current.IsSelected }

Act { (Pattern $win ([System.Windows.Automation.WindowPattern])).Close() }
Check 'application exits' 'exited' { if (Get-Process -Id $processId -ErrorAction SilentlyContinue) { 'running' } else { 'exited' } }

if ($failures -eq 0) { "ALL PASS" } else { "FAILURES $failures"; exit 1 }
