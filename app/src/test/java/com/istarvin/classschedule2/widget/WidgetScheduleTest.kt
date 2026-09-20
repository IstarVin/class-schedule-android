package com.istarvin.classschedule2.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetScheduleTest {
    private val zone = ZoneId.of("UTC")

    private val schedule = WidgetSchedule(
        listOf(
            ScheduleClass(DayOfWeek.MONDAY, 9 * 60, 10 * 60, "ITE183", "Web Systems", "TBA"),
            ScheduleClass(DayOfWeek.MONDAY, 13 * 60, 14 * 60, "ITE105", "Security", "TBA"),
            ScheduleClass(DayOfWeek.FRIDAY, 15 * 60, 16 * 60, "ITD111", "Business Intelligence", "TBA"),
        ),
    )

    @Test
    fun `during class shows current class and next class`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule, at(2026, 9, 21, 9, 30))

        assertEquals(WidgetState.CURRENT, snapshot.state)
        assertEquals("ITE183", snapshot.current?.code)
        assertEquals("ITE105", snapshot.next?.scheduleClass?.code)
        assertEquals(LocalDate.of(2026, 9, 21), snapshot.next?.date)
    }

    @Test
    fun `between classes makes the next class primary`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule, at(2026, 9, 21, 11, 30))

        assertEquals(WidgetState.UP_NEXT, snapshot.state)
        assertEquals("ITE105", snapshot.next?.scheduleClass?.code)
        assertNotNull(snapshot.refreshAt)
    }

    @Test
    fun `weekend refreshes at midnight instead of waiting for monday class`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule, at(2026, 9, 26, 12, 0))

        assertEquals(WidgetState.NO_CLASSES_TODAY, snapshot.state)
        assertEquals("ITE183", snapshot.next?.scheduleClass?.code)
        assertEquals(at(2026, 9, 27, 0, 0), snapshot.refreshAt)
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
