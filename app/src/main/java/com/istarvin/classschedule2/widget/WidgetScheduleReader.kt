package com.istarvin.classschedule2.widget

import android.content.Context
import com.istarvin.classschedule2.data.ScheduleCache
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/** Parses the cache written by [ScheduleCache]. This class intentionally never performs network I/O. */
internal object WidgetScheduleReader {
    fun load(context: Context): WidgetSchedule {
        val json = ScheduleCache.read(context) ?: return WidgetSchedule(emptyList())
        return runCatching { parse(json) }.getOrElse { WidgetSchedule(emptyList()) }
    }

    private fun parse(json: String): WidgetSchedule {
        val root = JSONObject(json)
        val subjects = root.optJSONObject("subjects") ?: JSONObject()
        val events = root.optJSONArray("events") ?: JSONArray()
        val classes = buildList {
            for (index in 0 until events.length()) {
                val event = events.optJSONObject(index) ?: continue
                val day = event.optInt("day", 0)
                val start = event.optInt("start", -1)
                val end = event.optInt("end", -1)
                val code = event.optString("code").trim()
                if (day !in 1..7 || start !in 0..1439 || end !in 1..1440 || end <= start || code.isEmpty()) {
                    continue
                }
                val subject = subjects.optJSONObject(code)
                add(
                    ScheduleClass(
                        day = DayOfWeek.of(day),
                        startMinute = start,
                        endMinute = end,
                        code = code,
                        section = subject?.optString("sec")?.trim().orEmpty(),
                        name = subject?.optString("name")?.trim().takeUnless { it.isNullOrEmpty() } ?: code,
                        room = event.optString("room").trim(),
                    ),
                )
            }
        }
        return WidgetSchedule(classes)
    }
}

internal class WidgetSchedule(private val classes: List<ScheduleClass>) {
    fun classesOn(date: LocalDate): List<ScheduleClass> {
        return classes.asSequence()
            .filter { it.day == date.dayOfWeek }
            .sortedBy { it.startMinute }
            .toList()
    }
}

/** Returns at most three entries, keeping the active or next entry in the middle when possible. */
internal fun centeredTodayWindow(
    classes: List<ScheduleClass>,
    anchor: ScheduleClass?,
): List<ScheduleClass> {
    if (classes.size <= 3) return classes

    val anchorIndex = anchor?.let(classes::indexOf)?.takeIf { it >= 0 } ?: 0
    val start = (anchorIndex - 1).coerceIn(0, classes.size - 3)
    return classes.subList(start, start + 3)
}
