import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dotktx.compose.ui.winui.Icon
import dotktx.compose.ui.winui.Button
import dotktx.compose.ui.winui.CalendarDatePicker
import dotktx.compose.ui.winui.CalendarView
import dotktx.compose.ui.winui.Canvas
import dotktx.compose.ui.winui.DatePicker
import dotktx.compose.ui.winui.Grid
import dotktx.compose.ui.winui.GridSize
import dotktx.compose.ui.winui.InfoBadge
import dotktx.compose.ui.winui.Modifier
import dotktx.compose.ui.winui.PersonPicture
import dotktx.compose.ui.winui.PipsPager
import dotktx.compose.ui.winui.SplitView
import dotktx.compose.ui.winui.StackPanel
import dotktx.compose.ui.winui.TextBlock
import dotktx.compose.ui.winui.TimePicker
import dotktx.compose.ui.winui.Viewbox
import dotktx.compose.ui.winui.Window
import dotktx.compose.ui.winui.application
import dotktx.compose.ui.winui.RelativePanel
import dotktx.compose.ui.winui.margin
import dotktx.compose.ui.winui.rememberWindowState
import dotktx.compose.ui.winui.size
import dotktx.compose.ui.winui.testTag
import Microsoft.UI.Xaml.Controls.Orientation
import Microsoft.UI.Xaml.Controls.SplitViewDisplayMode
import Microsoft.UI.Xaml.Controls.Symbol
import System.DateTimeOffset
import System.TimeSpan

// Noon UTC falls on the same calendar day in every time zone from UTC-11 to UTC+11, so the pickers,
// which show local dates, show the day the echo text names.
private fun day(year: Int, month: Int, day: Int): DateTimeOffset = DateTimeOffset(year, month, day, 12, 0, 0, TimeSpan.Zero)

private fun formatDate(date: DateTimeOffset?): String = if (date == null) "none" else "${date.Year}-${date.Month}-${date.Day}"

private fun formatTime(time: TimeSpan?): String =
    if (time == null) "none" else "${time.Hours.toString().padStart(2, '0')}:${time.Minutes.toString().padStart(2, '0')}"

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Pickers", state = rememberWindowState(width = 1100.0, height = 900.0)) {
        Grid(Modifier.margin(16.0), columns = listOf(GridSize.Star(), GridSize.Star()), columnSpacing = 24.0) {
            StackPanel(Modifier.cell(0, 0), spacing = 8.0) {
                DateAndTime()
                Calendars()
            }
            StackPanel(Modifier.cell(0, 1), spacing = 8.0) {
                Badges()
                Pager()
                Layouts()
            }
        }
    }
}

@Composable
private fun DateAndTime() {
    var date by remember { mutableStateOf<DateTimeOffset?>(day(2026, 3, 14)) }
    var time by remember { mutableStateOf<TimeSpan?>(TimeSpan(9, 30, 0)) }

    DatePicker(
        selectedDate = date,
        onSelectedDateChange = { date = it },
        modifier = Modifier.testTag("date"),
        header = "Date",
        minYear = day(2020, 1, 1),
        maxYear = day(2030, 12, 31),
    )
    TextBlock("date=${formatDate(date)}", Modifier.testTag("dateEcho"))
    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button({ date = day(2027, 7, 4) }, Modifier.testTag("setDate")) { TextBlock("Set 2027-7-4") }
        Button({ date = null }, Modifier.testTag("clearDate")) { TextBlock("Clear date") }
    }

    TimePicker(
        selectedTime = time,
        onSelectedTimeChange = { time = it },
        modifier = Modifier.testTag("time"),
        header = "Time",
        minuteIncrement = 5,
        clockIdentifier = "24HourClock",
    )
    TextBlock("time=${formatTime(time)}", Modifier.testTag("timeEcho"))
    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button({ time = TimeSpan(18, 45, 0) }, Modifier.testTag("setTime")) { TextBlock("Set 18:45") }
        Button({ time = null }, Modifier.testTag("clearTime")) { TextBlock("Clear time") }
    }
}

@Composable
private fun Calendars() {
    var calendarDate by remember { mutableStateOf<DateTimeOffset?>(null) }
    var selected by remember { mutableStateOf<DateTimeOffset?>(day(2026, 5, 10)) }

    CalendarDatePicker(
        date = calendarDate,
        onDateChange = { calendarDate = it },
        modifier = Modifier.testTag("calDate"),
        header = "Calendar date",
        placeholder = "Pick a date",
        minDate = day(2020, 1, 1),
        maxDate = day(2030, 12, 31),
    )
    TextBlock("calDate=${formatDate(calendarDate)}", Modifier.testTag("calDateEcho"))
    StackPanel(orientation = Orientation.Horizontal, spacing = 8.0) {
        Button({ calendarDate = day(2026, 2, 20) }, Modifier.testTag("setCalDate")) { TextBlock("Set 2026-2-20") }
        Button({ calendarDate = null }, Modifier.testTag("clearCalDate")) { TextBlock("Clear") }
    }

    CalendarView(
        selectedDate = selected,
        onSelectedDateChange = { selected = it },
        modifier = Modifier.testTag("calendar"),
        minDate = day(2020, 1, 1),
        maxDate = day(2030, 12, 31),
    )
    TextBlock("selected=${formatDate(selected)}", Modifier.testTag("calendarEcho"))
    Button({ selected = day(2026, 5, 25) }, Modifier.testTag("setCalendar")) { TextBlock("Select 2026-5-25") }
}

@Composable
private fun Badges() {
    var count by remember { mutableIntStateOf(3) }

    StackPanel(orientation = Orientation.Horizontal, spacing = 12.0) {
        // InfoBadge has no automation peer of its own; the buttons let the test find each badge's text.
        Button({}, Modifier.testTag("badgeValue")) { InfoBadge(value = count) }
        Button({}, Modifier.testTag("badgeIcon")) { InfoBadge(icon = Icon.Symbol(Symbol.Important)) }
        Button({}, Modifier.testTag("badgeDot")) { InfoBadge() }
        PersonPicture("Ann Lee", Modifier.size(48.0, 48.0).testTag("person"), badgeNumber = count)
        PersonPicture("Bob Stone", Modifier.size(48.0, 48.0).testTag("personInitials"), initials = "BS")
    }
    TextBlock("count=$count", Modifier.testTag("countEcho"))
    Button({ count++ }, Modifier.testTag("addCount")) { TextBlock("Add") }
}

@Composable
private fun Pager() {
    var page by remember { mutableIntStateOf(0) }

    PipsPager(
        numberOfPages = 5,
        selectedPageIndex = page,
        onSelectedPageIndexChange = { page = it },
        modifier = Modifier.testTag("pips"),
        navigationButtons = true,
    )
    TextBlock("page=$page", Modifier.testTag("pageEcho"))
    Button({ page = 4 }, Modifier.testTag("lastPage")) { TextBlock("Last page") }
}

@Composable
private fun Layouts() {
    var left by remember { mutableDoubleStateOf(10.0) }
    var paneOpen by remember { mutableStateOf(false) }

    Viewbox(Modifier.size(240.0, 60.0).testTag("viewbox")) {
        TextBlock("Scaled", Modifier.testTag("viewboxText"))
    }

    Canvas(Modifier.size(300.0, 60.0).testTag("canvas")) {
        TextBlock("Marker", Modifier.position(left, 20.0).testTag("marker"))
        TextBlock("Origin", Modifier.testTag("origin"))
    }
    TextBlock("left=${left.toInt()}", Modifier.testTag("leftEcho"))
    Button({ left += 50.0 }, Modifier.testTag("moveRight")) { TextBlock("Move right") }

    // The action button is emitted before the children it does not depend on, and the body before the
    // title it is placed against: constraints name their targets, so the order does not matter.
    var bodyBeside by remember { mutableStateOf(false) }
    RelativePanel(Modifier.size(360.0, 120.0)) {
        val (title, body, action) = createRefs()
        Button(
            { bodyBeside = !bodyBeside },
            Modifier.relative(action) { alignRightWithPanel(); alignBottomWithPanel() }.testTag("relAction"),
        ) { TextBlock("Move body") }
        TextBlock(
            "Body",
            Modifier.relative(body) {
                if (bodyBeside) {
                    rightOf(title)
                    alignTopWith(title)
                } else {
                    below(title)
                    alignLeftWith(title)
                }
            }.testTag("relBody"),
        )
        TextBlock("Title", Modifier.relative(title) { alignLeftWithPanel(); alignTopWithPanel() }.testTag("relTitle"))
    }
    TextBlock("body=${if (bodyBeside) "beside" else "below"}", Modifier.testTag("relEcho"))

    SplitView(
        paneOpen = paneOpen,
        onPaneOpenChange = { paneOpen = it },
        pane = { TextBlock("Pane content", Modifier.margin(12.0).testTag("paneText")) },
        modifier = Modifier.size(400.0, 160.0).testTag("split"),
        displayMode = SplitViewDisplayMode.Overlay,
        openPaneLength = 200.0,
    ) {
        StackPanel(Modifier.margin(12.0), spacing = 8.0) {
            TextBlock("pane=${if (paneOpen) "open" else "closed"}", Modifier.testTag("paneEcho"))
            Button({ paneOpen = true }, Modifier.testTag("openPane")) { TextBlock("Open pane") }
        }
    }
}
