package com.istarvin.classschedule2.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.istarvin.classschedule2.R

/** Stable IDs keep the app, offline cache, and every widget on the same schedule. */
enum class ScheduleProfile(
    val id: String,
    @param:StringRes val labelRes: Int,
    val host: String,
    val assetFileName: String,
    val metadataPreferencesName: String,
) {
    // Preserve existing installs' downloaded Alvin data and HTTP metadata.
    ALVIN("alvin", R.string.schedule_alvin, "ajschedule.pages.dev", "schedule.json", "schedule_cache_metadata"),
    SUE("sue", R.string.schedule_sue, "sue.ajschedule.pages.dev", "schedule_sue.json", "schedule_cache_metadata_sue");

    val siteUrl: String get() = "https://$host/"
    val remoteUrl: String get() = if (this == ALVIN) "${siteUrl}schedule.json" else siteUrl

    companion object {
        fun fromId(id: String?): ScheduleProfile = entries.firstOrNull { it.id == id } ?: ALVIN
    }
}

object SchedulePreferences {
    private const val PREFS_NAME = "schedule_settings"
    private const val KEY_PROFILE = "selected_profile"

    fun selected(context: Context): ScheduleProfile = ScheduleProfile.fromId(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROFILE, null),
    )

    fun select(context: Context, profile: ScheduleProfile) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_PROFILE, profile.id) }
    }
}
