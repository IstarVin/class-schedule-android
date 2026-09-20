package com.istarvin.classschedule2.data

import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A no-script fallback for when neither network nor WebView's page cache is available. */
object OfflineScheduleHtml {
    fun render(scheduleJson: String?, dark: Boolean): String {
        val palette = if (dark) {
            Palette("#15181E", "#1C212A", "#F1EFE6", "#8B94A3", "#4ADE80")
        } else {
            Palette("#F3F1EA", "#FFFFFF", "#1E2430", "#5B6270", "#1E8E52")
        }
        val schedule = parseToday(scheduleJson)
        val date = DateFormat.getDateInstance(DateFormat.FULL).format(Date()).escapeHtml()
        val body = if (schedule == null) {
            """
                <p class="empty">No saved schedule is available yet.</p>
                <p class="detail">Connect to the internet once to save your class schedule for offline use.</p>
            """.trimIndent()
        } else if (schedule.events.isEmpty()) {
            """
                <p class="empty">No classes scheduled today.</p>
                <p class="detail">This view is based on the latest schedule saved on this device.</p>
            """.trimIndent()
        } else {
            schedule.events.joinToString(separator = "\n") { event ->
                """
                    <article class="class-card">
                      <div class="time">${formatMinutes(event.start)}–${formatMinutes(event.end)}</div>
                      <div class="code">${event.code.escapeHtml()}</div>
                      <div class="name">${event.name.escapeHtml()}</div>
                      <div class="room">${event.room.escapeHtml()}</div>
                    </article>
                """.trimIndent()
            }
        }

        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>Saved schedule</title>
              <style>
                :root { color-scheme: ${if (dark) "dark" else "light"}; }
                * { box-sizing: border-box; }
                body { margin: 0; padding: 28px 16px 48px; background: ${palette.background}; color: ${palette.text}; font-family: sans-serif; }
                main { max-width: 720px; margin: 0 auto; }
                .eyebrow { color: ${palette.accent}; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
                h1 { margin: 7px 0 4px; font-size: 26px; line-height: 1.2; }
                .subtitle, .detail { color: ${palette.muted}; line-height: 1.45; }
                .subtitle { margin: 0 0 22px; font-size: 14px; }
                .class-card { margin: 10px 0; padding: 16px; border-radius: 14px; background: ${palette.card}; }
                .time { color: ${palette.muted}; font-size: 13px; margin-bottom: 8px; }
                .code { color: ${palette.accent}; font-size: 13px; font-weight: 700; }
                .name { margin-top: 4px; font-size: 18px; font-weight: 650; line-height: 1.3; }
                .room { margin-top: 5px; color: ${palette.muted}; font-size: 14px; }
                .empty { margin: 34px 0 8px; font-size: 18px; font-weight: 650; }
              </style>
            </head>
            <body>
              <main>
                <div class="eyebrow">SAVED FOR OFFLINE</div>
                <h1>Today’s schedule</h1>
                <p class="subtitle">$date · Showing the last schedule stored on this device.</p>
                $body
              </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun parseToday(json: String?): DaySchedule? = try {
        val root = JSONObject(json ?: return null)
        val subjects = root.getJSONObject("subjects")
        val today = scheduleDay(Calendar.getInstance())
        val events = mutableListOf<CachedEvent>()
        val sourceEvents = root.getJSONArray("events")
        for (index in 0 until sourceEvents.length()) {
            val item = sourceEvents.getJSONObject(index)
            if (item.optInt("day") != today) continue
            val code = item.optString("code")
            val subject = subjects.optJSONObject(code)
            events += CachedEvent(
                start = item.optInt("start"),
                end = item.optInt("end"),
                code = code,
                name = subject?.optString("name").orEmpty().ifBlank { code },
                room = item.optString("room").ifBlank { "Room not listed" },
            )
        }
        DaySchedule(coalesce(events.sortedBy(CachedEvent::start)))
    } catch (_: Exception) {
        null
    }

    private fun coalesce(events: List<CachedEvent>): List<CachedEvent> {
        val merged = mutableListOf<CachedEvent>()
        for (event in events) {
            val previous = merged.lastOrNull()
            if (previous != null && previous.code == event.code && previous.room == event.room && previous.end == event.start) {
                previous.end = event.end
            } else {
                merged += event
            }
        }
        return merged
    }

    private fun scheduleDay(calendar: Calendar): Int = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }

    private fun formatMinutes(minutes: Int): String {
        val hour24 = (minutes / 60).coerceIn(0, 23)
        val minute = (minutes % 60).coerceIn(0, 59)
        val suffix = if (hour24 < 12) "AM" else "PM"
        val hour12 = when (val value = hour24 % 12) { 0 -> 12; else -> value }
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, suffix)
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private data class Palette(
        val background: String,
        val card: String,
        val text: String,
        val muted: String,
        val accent: String,
    )

    private data class DaySchedule(val events: List<CachedEvent>)

    private data class CachedEvent(
        val start: Int,
        var end: Int,
        val code: String,
        val name: String,
        val room: String,
    )
}
