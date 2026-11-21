package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import Microsoft.UI.Xaml.GridLength
import Microsoft.UI.Xaml.GridUnitType
import Microsoft.UI.Xaml.Controls.ColumnDefinition
import Microsoft.UI.Xaml.Controls.RowDefinition
import Microsoft.UI.Xaml.Controls.Grid as XamlGrid

/** Size of one `Grid` row or column. */
sealed class GridSize {
    /** As large as the content needs. */
    object Auto : GridSize()

    /** A share of the space left after [Auto] and [Pixels] tracks, proportional to [weight]. */
    data class Star(val weight: Double = 1.0) : GridSize()

    /** A fixed size in effective pixels. */
    data class Pixels(val value: Double) : GridSize()
}

private fun GridSize.toGridLength(): GridLength = when (this) {
    GridSize.Auto -> GridLength(1.0, GridUnitType.Auto)
    is GridSize.Star -> GridLength(weight, GridUnitType.Star)
    is GridSize.Pixels -> GridLength(value, GridUnitType.Pixel)
}

/**
 * Arranges its children in rows and columns (`Grid`). Children pick their cell with
 * [Modifier.gridCell]; without it they go to row 0, column 0. An empty [rows] or [columns] list means
 * one track that fills the grid.
 *
 * ```
 * Grid(rows = listOf(GridSize.Auto, GridSize.Star()), columns = listOf(GridSize.Pixels(120.0), GridSize.Star())) {
 *     TextBlock("Name", Modifier.gridCell(0, 0))
 *     TextBox(name, { name = it }, Modifier.gridCell(0, 1))
 * }
 * ```
 */
@Composable
fun Grid(
    modifier: Modifier = Modifier,
    rows: List<GridSize> = emptyList(),
    columns: List<GridSize> = emptyList(),
    rowSpacing: Double = 0.0,
    columnSpacing: Double = 0.0,
    content: @Composable () -> Unit,
) {
    Container({ gridNode(XamlGrid()) }, modifier, {
        set(rows) { sizes ->
            element.RowDefinitions.Clear()
            for (size in sizes) element.RowDefinitions.Add(RowDefinition().also { it.Height = size.toGridLength() })
        }
        set(columns) { sizes ->
            element.ColumnDefinitions.Clear()
            for (size in sizes) element.ColumnDefinitions.Add(ColumnDefinition().also { it.Width = size.toGridLength() })
        }
        set(rowSpacing) { element.RowSpacing = it }
        set(columnSpacing) { element.ColumnSpacing = it }
    }, content)
}

private fun gridNode(grid: XamlGrid): ElementNode<XamlGrid> = ElementNode(grid, ChildSlot.Many(grid.Children))
