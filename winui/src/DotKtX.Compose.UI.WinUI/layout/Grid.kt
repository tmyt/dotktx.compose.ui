package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.FrameworkElement
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
 * [GridScope.cell]; without it they go to row 0, column 0. An empty [rows] or [columns] list means
 * one track that fills the grid.
 *
 * ```
 * Grid(rows = listOf(GridSize.Auto, GridSize.Star()), columns = listOf(GridSize.Pixels(120.0), GridSize.Star())) {
 *     TextBlock("Name", Modifier.cell(0, 0))
 *     TextBox(name, { name = it }, Modifier.cell(0, 1))
 * }
 * ```
 */
@EmitsOneElement
@Composable
fun Grid(
    modifier: Modifier = Modifier,
    rows: List<GridSize> = emptyList(),
    columns: List<GridSize> = emptyList(),
    rowSpacing: Double = 0.0,
    columnSpacing: Double = 0.0,
    content: @Composable GridScope.() -> Unit,
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
    }) { GridScope.content() }
}

private fun gridNode(grid: XamlGrid): ElementNode<XamlGrid> = ElementNode(grid, ChildSlot.Many(grid.Children))

/** Receiver of [Grid]'s content. */
@LayoutScopeMarker
object GridScope {
    /** Cell of this child (`Grid.Row`, `Grid.Column`, `Grid.RowSpan`, `Grid.ColumnSpan`). */
    fun Modifier.cell(row: Int, column: Int, rowSpan: Int = 1, columnSpan: Int = 1): Modifier =
        this then GridCellModifier(row, column, rowSpan, columnSpan)
}

private data class GridCellModifier(val row: Int, val column: Int, val rowSpan: Int, val columnSpan: Int) : ElementModifier {
    override val key: Any get() = GridCellModifier::class

    override fun apply(element: FrameworkElement) {
        XamlGrid.SetRow(element, row)
        XamlGrid.SetColumn(element, column)
        XamlGrid.SetRowSpan(element, rowSpan)
        XamlGrid.SetColumnSpan(element, columnSpan)
    }

    override fun reset(element: FrameworkElement) {
        element.ClearValue(XamlGrid.RowProperty)
        element.ClearValue(XamlGrid.ColumnProperty)
        element.ClearValue(XamlGrid.RowSpanProperty)
        element.ClearValue(XamlGrid.ColumnSpanProperty)
    }
}
