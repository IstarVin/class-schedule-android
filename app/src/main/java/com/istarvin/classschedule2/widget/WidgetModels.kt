package com.istarvin.classschedule2.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/** A widget-specific view of the shared schedule cache. */
internal data class ScheduleClass(
    val day: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
    val code: String,
    val section: String,
    val name: String,
    val room: String,
)

internal data class ScheduledClass(
    val scheduleClass: ScheduleClass,
    val date: LocalDate,
)

internal enum class WidgetState {
    CURRENT,
    UP_NEXT,
    DONE_FOR_TODAY,
    NO_CLASSES_TODAY,
    EMPTY,
}

internal data class WidgetSnapshot(
    val now: ZonedDateTime,
    val today: List<ScheduleClass>,
    val todayWindow: List<ScheduleClass>,
    val current: ScheduleClass?,
    val next: ScheduledClass?,
    val following: ScheduledClass?,
    val state: WidgetState,
    val refreshAt: ZonedDateTime,
)

enum class WidgetLayout {
    COMPACT,
    MEDIUM,
    LARGE,
    STACKED,
}

internal enum class WidgetAppearance(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStored(value: String?): WidgetAppearance =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

internal enum class WidgetColorSource(val storedValue: String) {
    CLASS_SCHEDULE("class_schedule"),
    MATERIAL_YOU("material_you");

    companion object {
        fun fromStored(value: String?): WidgetColorSource =
            entries.firstOrNull { it.storedValue == value } ?: CLASS_SCHEDULE
    }
}

internal enum class WidgetTimeFormat(val storedValue: String) {
    SYSTEM("system"),
    HOUR_12("12_hour"),
    HOUR_24("24_hour");

    companion object {
        fun fromStored(value: String?): WidgetTimeFormat =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

internal data class WidgetOptions(
    val appearance: WidgetAppearance = WidgetAppearance.SYSTEM,
    val transparencyPercent: Int = 100,
    val panelTransparencyPercent: Int = 15,
    val colorSource: WidgetColorSource = WidgetColorSource.CLASS_SCHEDULE,
    val timeFormat: WidgetTimeFormat = WidgetTimeFormat.SYSTEM,
    val showCountdown: Boolean = true,
    val showRoom: Boolean = true,
    val showNext: Boolean = true,
    val showProgress: Boolean = true,
)
