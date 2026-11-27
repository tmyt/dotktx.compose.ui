package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.Controls.CalendarViewSelectionMode
import System.DateTimeOffset
import System.TimeSpan
import Microsoft.UI.Xaml.Controls.CalendarDatePicker as XamlCalendarDatePicker
import Microsoft.UI.Xaml.Controls.CalendarView as XamlCalendarView
import Microsoft.UI.Xaml.Controls.DatePicker as XamlDatePicker
import Microsoft.UI.Xaml.Controls.TimePicker as XamlTimePicker

/**
 * A date chosen from day, month and year spinners in a flyout (`DatePicker`), controlled by
 * [selectedDate]. `null` shows the placeholder instead of a date; the user cannot clear a date.
 *
 * Only the date part is meaningful; the time of day of the reported value is whatever WinUI keeps.
 * [minYear] and [maxYear] limit the years offered; `null` keeps WinUI's range (100 years either side
 * of today).
 *
 * ```
 * DatePicker(selectedDate = due, onSelectedDateChange = { due = it }, header = "Due date")
 * ```
 */
@EmitsOneElement
@Composable
fun DatePicker(
    selectedDate: DateTimeOffset?,
    onSelectedDateChange: (DateTimeOffset?) -> Unit,
    modifier: Modifier = Modifier,
    header: String = "",
    minYear: DateTimeOffset? = null,
    maxYear: DateTimeOffset? = null,
    enabled: Boolean = true,
) {
    val scope = currentRecomposeScope
    Leaf({ DatePickerNode(XamlDatePicker()) }, modifier) {
        set(scope) { input.scope = it }
        set(onSelectedDateChange) { this.onSelectedDateChange = it }
        set(enabled) { element.IsEnabled = it }
        set(header) { if (it.isEmpty()) element.ClearValue(XamlDatePicker.HeaderProperty) else element.Header = it }
        // The range must be in place before the date is synced, or WinUI clamps the date to the old range.
        set(minYear) { year ->
            input.programmatic { if (year == null) element.ClearValue(XamlDatePicker.MinYearProperty) else element.MinYear = year }
        }
        set(maxYear) { year ->
            input.programmatic { if (year == null) element.ClearValue(XamlDatePicker.MaxYearProperty) else element.MaxYear = year }
        }
        reconcile { input.sync(selectedDate) }
    }
}

internal class DatePickerNode(picker: XamlDatePicker) : ElementNode<XamlDatePicker>(picker, ChildSlot.None("DatePicker")) {
    var onSelectedDateChange: (DateTimeOffset?) -> Unit = {}

    val input = ControlledValue<DateTimeOffset?>(read = { picker.SelectedDate }, write = { picker.SelectedDate = it })

    init {
        // SelectedDateChanged is raised from the property change itself, so the value is current.
        onRelease(picker.SelectedDateChanged.subscribe { _, _ -> input.userChanged(onSelectedDateChange) })
    }
}

/**
 * A time of day chosen from hour and minute spinners in a flyout (`TimePicker`), controlled by
 * [selectedTime], the time since midnight. `null` shows the placeholder instead of a time; the user
 * cannot clear a time.
 *
 * [minuteIncrement] is the step of the minute spinner. [clockIdentifier] is `"12HourClock"` or
 * `"24HourClock"`; empty follows the user's region settings.
 *
 * ```
 * TimePicker(selectedTime = alarm, onSelectedTimeChange = { alarm = it }, minuteIncrement = 5)
 * ```
 */
@EmitsOneElement
@Composable
fun TimePicker(
    selectedTime: TimeSpan?,
    onSelectedTimeChange: (TimeSpan?) -> Unit,
    modifier: Modifier = Modifier,
    header: String = "",
    minuteIncrement: Int = 1,
    clockIdentifier: String = "",
    enabled: Boolean = true,
) {
    val scope = currentRecomposeScope
    Leaf({ TimePickerNode(XamlTimePicker()) }, modifier) {
        set(scope) { input.scope = it }
        set(onSelectedTimeChange) { this.onSelectedTimeChange = it }
        set(enabled) { element.IsEnabled = it }
        set(header) { if (it.isEmpty()) element.ClearValue(XamlTimePicker.HeaderProperty) else element.Header = it }
        set(minuteIncrement) { element.MinuteIncrement = it }
        set(clockIdentifier) {
            if (it.isEmpty()) element.ClearValue(XamlTimePicker.ClockIdentifierProperty) else element.ClockIdentifier = it
        }
        reconcile { input.sync(selectedTime) }
    }
}

internal class TimePickerNode(picker: XamlTimePicker) : ElementNode<XamlTimePicker>(picker, ChildSlot.None("TimePicker")) {
    var onSelectedTimeChange: (TimeSpan?) -> Unit = {}

    val input = ControlledValue<TimeSpan?>(read = { picker.SelectedTime }, write = { picker.SelectedTime = it })

    init {
        // SelectedTimeChanged is raised from the property change itself, so the value is current.
        onRelease(picker.SelectedTimeChanged.subscribe { _, _ -> input.userChanged(onSelectedTimeChange) })
    }
}

/**
 * A text field that opens a calendar to choose a date (`CalendarDatePicker`), controlled by [date].
 * `null` shows [placeholder]. [minDate] and [maxDate] limit the dates offered; `null` keeps WinUI's
 * range.
 *
 * ```
 * CalendarDatePicker(date = start, onDateChange = { start = it }, placeholder = "Pick a date")
 * ```
 */
@EmitsOneElement
@Composable
fun CalendarDatePicker(
    date: DateTimeOffset?,
    onDateChange: (DateTimeOffset?) -> Unit,
    modifier: Modifier = Modifier,
    header: String = "",
    placeholder: String = "",
    minDate: DateTimeOffset? = null,
    maxDate: DateTimeOffset? = null,
    enabled: Boolean = true,
) {
    val scope = currentRecomposeScope
    Leaf({ CalendarDatePickerNode(XamlCalendarDatePicker()) }, modifier) {
        set(scope) { input.scope = it }
        set(onDateChange) { this.onDateChange = it }
        set(enabled) { element.IsEnabled = it }
        set(header) { if (it.isEmpty()) element.ClearValue(XamlCalendarDatePicker.HeaderProperty) else element.Header = it }
        set(placeholder) {
            if (it.isEmpty()) element.ClearValue(XamlCalendarDatePicker.PlaceholderTextProperty) else element.PlaceholderText = it
        }
        // The range must be in place before the date is synced, or WinUI clears a date outside the old range.
        set(minDate) { min ->
            input.programmatic { if (min == null) element.ClearValue(XamlCalendarDatePicker.MinDateProperty) else element.MinDate = min }
        }
        set(maxDate) { max ->
            input.programmatic { if (max == null) element.ClearValue(XamlCalendarDatePicker.MaxDateProperty) else element.MaxDate = max }
        }
        reconcile { input.sync(date) }
    }
}

internal class CalendarDatePickerNode(picker: XamlCalendarDatePicker) :
    ElementNode<XamlCalendarDatePicker>(picker, ChildSlot.None("CalendarDatePicker")) {
    var onDateChange: (DateTimeOffset?) -> Unit = {}

    val input = ControlledValue<DateTimeOffset?>(read = { picker.Date }, write = { picker.Date = it })

    init {
        // DateChanged is raised from the property change itself, so the value is current.
        onRelease(picker.DateChanged.subscribe { _, _ -> input.userChanged(onDateChange) })
    }
}

/**
 * A month calendar (`CalendarView`) with single selection, controlled by [selectedDate] (`null` for
 * no selection). Selecting the selected day again clears the selection, which reports `null`. When
 * the application changes [selectedDate], the calendar scrolls to the new date. [minDate] and [maxDate] limit the dates shown; `null` keeps WinUI's range.
 *
 * ```
 * CalendarView(selectedDate = day, onSelectedDateChange = { day = it })
 * ```
 */
@EmitsOneElement
@Composable
fun CalendarView(
    selectedDate: DateTimeOffset?,
    onSelectedDateChange: (DateTimeOffset?) -> Unit,
    modifier: Modifier = Modifier,
    minDate: DateTimeOffset? = null,
    maxDate: DateTimeOffset? = null,
    enabled: Boolean = true,
) {
    val scope = currentRecomposeScope
    Leaf({ CalendarViewNode(XamlCalendarView()) }, modifier) {
        set(scope) { input.scope = it }
        set(onSelectedDateChange) { this.onSelectedDateChange = it }
        set(enabled) { element.IsEnabled = it }
        set(minDate) { min ->
            input.programmatic { if (min == null) element.ClearValue(XamlCalendarView.MinDateProperty) else element.MinDate = min }
        }
        set(maxDate) { max ->
            input.programmatic { if (max == null) element.ClearValue(XamlCalendarView.MaxDateProperty) else element.MaxDate = max }
        }
        reconcile { input.sync(selectedDate) }
    }
}

internal class CalendarViewNode(view: XamlCalendarView) : ElementNode<XamlCalendarView>(view, ChildSlot.None("CalendarView")) {
    var onSelectedDateChange: (DateTimeOffset?) -> Unit = {}

    val input = ControlledValue<DateTimeOffset?>(
        read = { val dates = view.SelectedDates; if (dates.Count > 0) dates[0] else null },
        write = { date ->
            view.SelectedDates.Clear()
            if (date != null) {
                view.SelectedDates.Add(date)
                // CalendarView keeps showing the month it shows; bring a date the application chose into view.
                view.SetDisplayDate(date)
            }
        },
    )

    init {
        view.SelectionMode = CalendarViewSelectionMode.Single
        onRelease(view.SelectedDatesChanged.subscribe { _, _ -> input.userChanged(onSelectedDateChange) })
    }
}
