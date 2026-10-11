package com.istarvin.classschedule2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScheduleProfileTest {
    @Test
    fun `existing installs and unknown saved IDs default to Alvin`() {
        assertEquals(ScheduleProfile.ALVIN, ScheduleProfile.fromId(null))
        assertEquals(ScheduleProfile.ALVIN, ScheduleProfile.fromId("removed-profile"))
        assertEquals("schedule.json", ScheduleProfile.ALVIN.assetFileName)
        assertEquals("schedule_cache_metadata", ScheduleProfile.ALVIN.metadataPreferencesName)
    }

    @Test
    fun `persisted profile IDs resolve independently of labels`() {
        ScheduleProfile.entries.forEach { profile ->
            assertEquals(profile, ScheduleProfile.fromId(profile.id))
        }
    }

    @Test
    fun `Sue uses independent cache and HTTP metadata`() {
        assertNotEquals(ScheduleProfile.ALVIN.assetFileName, ScheduleProfile.SUE.assetFileName)
        assertNotEquals(ScheduleProfile.ALVIN.metadataPreferencesName, ScheduleProfile.SUE.metadataPreferencesName)
        assertEquals("https://sue.ajschedule.pages.dev/", ScheduleProfile.SUE.remoteUrl)
        assertEquals("https://ajschedule.pages.dev/schedule.json", ScheduleProfile.ALVIN.remoteUrl)
    }
}
