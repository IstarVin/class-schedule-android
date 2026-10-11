package com.istarvin.classschedule2.widget

import com.istarvin.classschedule2.data.ScheduleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetScheduleProfilesTest {
    @Test
    fun `switching profiles changes widget data at the same instant`() {
        val now = at(2026, 10, 12, 9, 30)
        val alvin = WidgetUpdateCoordinator.snapshotFor(schedule(ScheduleProfile.ALVIN), now)
        val sue = WidgetUpdateCoordinator.snapshotFor(schedule(ScheduleProfile.SUE), now)

        assertEquals("ITD112", alvin.current?.code)
        assertEquals("BDV108", sue.current?.code)
        assertEquals("Philippine Plants", sue.current?.name)
        assertEquals("BDV104", sue.next?.scheduleClass?.code)
        assertEquals(at(2026, 10, 12, 10, 0), sue.refreshAt)
    }

    @Test
    fun `Sue class end changes to up next at the exact boundary`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule(ScheduleProfile.SUE), at(2026, 10, 12, 10, 0))
        assertEquals(WidgetState.UP_NEXT, snapshot.state)
        assertNull(snapshot.current)
        assertEquals("BDV104", snapshot.next?.scheduleClass?.code)
    }

    @Test
    fun `Sue Wednesday class appears after the date changes`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule(ScheduleProfile.SUE), at(2026, 10, 14, 0, 0))
        assertEquals("BDV198", snapshot.next?.scheduleClass?.code)
        assertEquals(LocalDate.of(2026, 10, 14), snapshot.next?.date)
        assertEquals(at(2026, 10, 14, 8, 0), snapshot.refreshAt)
    }

    @Test
    fun `Sue weekend rolls over to Monday`() {
        val snapshot = WidgetUpdateCoordinator.snapshotFor(schedule(ScheduleProfile.SUE), at(2026, 10, 18, 12, 0))
        assertEquals(WidgetState.NO_CLASSES_TODAY, snapshot.state)
        assertEquals("BDV108", snapshot.next?.scheduleClass?.code)
        assertEquals(LocalDate.of(2026, 10, 19), snapshot.next?.date)
    }

    @Test
    fun `simultaneous Sue lecture and laboratory entries are both preserved`() {
        val tuesday = schedule(ScheduleProfile.SUE).classesOn(LocalDate.of(2026, 10, 13))
        assertEquals(listOf("PHY011", "PHY011.2"), tuesday.take(2).map { it.code })
        assertEquals(listOf(480, 480), tuesday.take(2).map { it.startMinute })
    }

    private fun schedule(profile: ScheduleProfile): WidgetSchedule {
        val json = checkNotNull(javaClass.classLoader?.getResourceAsStream(profile.assetFileName))
            .bufferedReader().use { it.readText() }
        return WidgetScheduleReader.parse(json)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of("Asia/Manila"))
}
