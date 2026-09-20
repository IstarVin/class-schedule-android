package com.istarvin.classschedule2.widget

import android.content.Context

/** Per-widget preferences. Keeping keys scoped by widget ID avoids leaking a removed widget's style. */
internal object WidgetPreferences {
    private const val PREFS_NAME = "aj_schedule_widget_preferences"

    private const val STYLE = "style"
    private const val APPEARANCE = "appearance"
    private const val COLOR_SOURCE = "color_source"
    private const val TIME_FORMAT = "time_format"
    private const val SHOW_COUNTDOWN = "show_countdown"
    private const val SHOW_ROOM = "show_room"
    private const val SHOW_NEXT = "show_next"
    private const val SHOW_PROGRESS = "show_progress"

    fun read(context: Context, appWidgetId: Int): WidgetOptions {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetOptions(
            style = WidgetStyle.fromStored(prefs.getString(key(appWidgetId, STYLE), null)),
            appearance = WidgetAppearance.fromStored(prefs.getString(key(appWidgetId, APPEARANCE), null)),
            colorSource = WidgetColorSource.fromStored(prefs.getString(key(appWidgetId, COLOR_SOURCE), null)),
            timeFormat = WidgetTimeFormat.fromStored(prefs.getString(key(appWidgetId, TIME_FORMAT), null)),
            showCountdown = prefs.getBoolean(key(appWidgetId, SHOW_COUNTDOWN), true),
            showRoom = prefs.getBoolean(key(appWidgetId, SHOW_ROOM), true),
            showNext = prefs.getBoolean(key(appWidgetId, SHOW_NEXT), true),
            showProgress = prefs.getBoolean(key(appWidgetId, SHOW_PROGRESS), true),
        )
    }

    fun save(context: Context, appWidgetId: Int, options: WidgetOptions) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(appWidgetId, STYLE), options.style.storedValue)
            .putString(key(appWidgetId, APPEARANCE), options.appearance.storedValue)
            .putString(key(appWidgetId, COLOR_SOURCE), options.colorSource.storedValue)
            .putString(key(appWidgetId, TIME_FORMAT), options.timeFormat.storedValue)
            .putBoolean(key(appWidgetId, SHOW_COUNTDOWN), options.showCountdown)
            .putBoolean(key(appWidgetId, SHOW_ROOM), options.showRoom)
            .putBoolean(key(appWidgetId, SHOW_NEXT), options.showNext)
            .putBoolean(key(appWidgetId, SHOW_PROGRESS), options.showProgress)
            .apply()
    }

    fun remove(context: Context, appWidgetIds: IntArray) {
        val editor = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { id ->
            listOf(STYLE, APPEARANCE, COLOR_SOURCE, TIME_FORMAT, SHOW_COUNTDOWN, SHOW_ROOM, SHOW_NEXT, SHOW_PROGRESS)
                .forEach { field -> editor.remove(key(id, field)) }
        }
        editor.apply()
    }

    fun migrate(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.zip(newWidgetIds).forEach { (oldId, newId) ->
            save(context, newId, read(context, oldId))
        }
        remove(context, oldWidgetIds)
    }

    private fun key(appWidgetId: Int, field: String) = "widget_${appWidgetId}_$field"
}
