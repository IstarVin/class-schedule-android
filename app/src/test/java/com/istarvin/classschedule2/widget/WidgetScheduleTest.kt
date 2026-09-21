package com.istarvin.classschedule2.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class WidgetScheduleTest {
    private val zone = ZoneId.of("UTC")

    private val schedule = WidgetSchedule(
        listOf(
            ScheduleClass(DayOfWeek.MONDAY, 9 * 60, 10 * 60, "ITE183", "", "Web Systems", "TBA"),
            ScheduleClass(DayOfWeek.MONDAY, 13 * 60, 14 * 60, "ITE105", "", "Security", "TBA"),
            ScheduleClass(DayOfWeek.FRIDAY, 15 * 60, 16 * 60, "ITD111", "", "Business Intelligence", "TBA"),
        ),
    )

    private val fiveEventSchedule = WidgetSchedule(
        listOf(
            ScheduleClass(DayOfWeek.MONDAY, 9 * 60, 10 * 60, "ITD112", "", "First", "TBA"),
            ScheduleClass(DayOfWeek.MONDAY, 10 * 60, 11 * 60, "ITD112", "", "Second", "TBA"),
            ScheduleClass(DayOfWeek.MONDAY, 13 * 60, 14 * 60, "ITE104", "", "Third", "TBA"),
            ScheduleClass(DayOfWeek.MONDAY, 14 * 60, 15 * 60, "ITD111", "", "Fourth", "TBA"),
            ScheduleClass(DayOfWeek.MONDAY, 15 * 60, 16 * 60, "ITD111", "", "Fifth", "TBA"),
        ),
    )

    private val fiveEventTuesdaySchedule = WidgetSchedule(
        listOf(
            ScheduleClass(DayOfWeek.TUESDAY, 9 * 60, 10 * 60, "ITD112", "", "First", "TBA"),
            ScheduleClass(DayOfWeek.TUESDAY, 10 * 60, 11 * 60, "ITD112", "", "Second", "TBA"),
            ScheduleClass(DayOfWeek.TUESDAY, 13 * 60, 14 * 60, "ITE104", "", "Third", "TBA"),
            ScheduleClass(DayOfWeek.TUESDAY, 14 * 60, 15 * 60, "ITD111", "", "Fourth", "TBA"),
            ScheduleClass(DayOfWeek.TUESDAY, 15 * 60, 16 * 60, "ITD111", "", "Fifth", "TBA"),
        ),
    )

    @Test
    fun `during class shows current class and next class`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule, at(2026, 9, 21, 9, 30))

        assertEquals(WidgetState.CURRENT, snapshot.state)
        assertEquals("ITE183", snapshot.current?.code)
        assertEquals("ITE105", snapshot.next?.scheduleClass?.code)
        assertEquals(LocalDate.of(2026, 9, 21), snapshot.next?.date)
        assertEquals(at(2026, 9, 21, 10, 0), snapshot.refreshAt)
    }

    @Test
    fun `today window keeps raw events and centers the current event`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(fiveEventSchedule, at(2026, 9, 21, 13, 30))

        assertEquals(5, snapshot.today.size)
        assertEquals(listOf("ITD112", "ITE104", "ITD111"), snapshot.todayWindow.map(ScheduleClass::code))
        assertEquals("ITE104", snapshot.current?.code)
        assertEquals("ITE104", snapshot.todayWindow[1].code)
    }

    @Test
    fun `today window is clamped when current event is at the end of the day`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(fiveEventSchedule, at(2026, 9, 21, 15, 30))

        assertEquals(listOf("ITE104", "ITD111", "ITD111"), snapshot.todayWindow.map(ScheduleClass::code))
        assertEquals("ITD111", snapshot.current?.code)
        assertEquals("ITD111", snapshot.todayWindow[2].code)
    }

    @Test
    fun `after the last class keeps the final three classes visible`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(fiveEventSchedule, at(2026, 9, 21, 17, 0))

        assertEquals(WidgetState.DONE_FOR_TODAY, snapshot.state)
        assertEquals(listOf("ITE104", "ITD111", "ITD111"), snapshot.todayWindow.map(ScheduleClass::code))
    }

    @Test
    fun `midnight starts the window with the first classes of the new day`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(fiveEventTuesdaySchedule, at(2026, 9, 22, 0, 0))

        assertEquals(listOf("ITD112", "ITD112", "ITE104"), snapshot.todayWindow.map(ScheduleClass::code))
        assertEquals(WidgetState.UP_NEXT, snapshot.state)
        assertEquals("ITD112", snapshot.next?.scheduleClass?.code)
    }

    @Test
    fun `between classes makes the next class primary`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule, at(2026, 9, 21, 11, 30))

        assertEquals(WidgetState.UP_NEXT, snapshot.state)
        assertEquals("ITE105", snapshot.next?.scheduleClass?.code)
        assertEquals(at(2026, 9, 21, 13, 0), snapshot.refreshAt)
    }

    @Test
    fun `before a class refreshes at its start boundary`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule, at(2026, 9, 21, 8, 0))

        assertEquals(WidgetState.UP_NEXT, snapshot.state)
        assertEquals(at(2026, 9, 21, 9, 0), snapshot.refreshAt)
    }

    @Test
    fun `at a class start immediately shows the current class`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule, at(2026, 9, 21, 13, 0))

        assertEquals(WidgetState.CURRENT, snapshot.state)
        assertEquals("ITE105", snapshot.current?.code)
        assertEquals(at(2026, 9, 21, 14, 0), snapshot.refreshAt)
    }

    @Test
    fun `weekend refreshes at midnight instead of waiting for monday class`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule, at(2026, 9, 26, 12, 0))

        assertEquals(WidgetState.NO_CLASSES_TODAY, snapshot.state)
        assertEquals("ITE183", snapshot.next?.scheduleClass?.code)
        assertEquals(LocalDate.of(2026, 9, 28), snapshot.next?.date)
        assertEquals(at(2026, 9, 27, 0, 0), snapshot.refreshAt)
    }

    @Test
    fun `stacked next label calls out tomorrow and later weekday dates`() {
        val today = LocalDate.of(2026, 9, 26)

        assertEquals(
            "Tomorrow",
            stackedDateLabel(today.plusDays(1), today, "Today", "Tomorrow", Locale.US),
        )
        assertEquals(
            "Monday · Sep 28",
            stackedDateLabel(today.plusDays(2), today, "Today", "Tomorrow", Locale.US),
        )
    }

    @Test
    fun `after the final class refreshes at the next date boundary`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule, at(2026, 9, 25, 17, 0))

        assertEquals(WidgetState.DONE_FOR_TODAY, snapshot.state)
        assertEquals(at(2026, 9, 26, 0, 0), snapshot.refreshAt)
    }

    @Test
    fun `final class keeps its current state while pointing to next week`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule, at(2026, 9, 25, 15, 30))

        assertEquals(WidgetState.CURRENT, snapshot.state)
        assertEquals("ITD111", snapshot.current?.code)
        assertEquals(LocalDate.of(2026, 9, 28), snapshot.next?.date)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
}
