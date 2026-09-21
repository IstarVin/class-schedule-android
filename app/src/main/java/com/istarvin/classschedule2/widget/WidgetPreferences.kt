package com.istarvin.classschedule2.widget

import android.content.Context
import androidx.core.content.edit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Per-widget preferences. Keeping keys scoped by widget ID avoids leaking a removed widget's settings. */
internal object WidgetPreferences {
    private const val PREFS_NAME = "class_schedule_widget_preferences"

    /**
     * Glance keeps a composition alive for a short period after it renders a widget. Exposing
     * changes in-process lets that composition observe settings saved while it is still alive.
     */
    private val optionFlows = ConcurrentHashMap<Int, MutableStateFlow<WidgetOptions>>()

    private const val APPEARANCE = "appearance"
    private const val TRANSPARENCY_PERCENT = "transparency_percent"
    private const val PANEL_TRANSPARENCY_PERCENT = "panel_transparency_percent"
    private const val LEGACY_TRANSPARENCY = "transparency"
    private const val COLOR_SOURCE = "color_source"
    private const val TIME_FORMAT = "time_format"
    private const val SHOW_COUNTDOWN = "show_countdown"
    private const val SHOW_ROOM = "show_room"
    private const val SHOW_NEXT = "show_next"
    private const val SHOW_PROGRESS = "show_progress"

    fun read(context: Context, appWidgetId: Int): WidgetOptions {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetOptions(
            appearance = WidgetAppearance.fromStored(prefs.getString(key(appWidgetId, APPEARANCE), null)),
            transparencyPercent = prefs.getInt(
                key(appWidgetId, TRANSPARENCY_PERCENT),
                legacyTransparencyPercent(prefs.getString(key(appWidgetId, LEGACY_TRANSPARENCY), null)),
            ).coerceIn(0, 100),
            panelTransparencyPercent = prefs.getInt(
                key(appWidgetId, PANEL_TRANSPARENCY_PERCENT),
                15,
            ).coerceIn(0, 100),
            colorSource = WidgetColorSource.fromStored(prefs.getString(key(appWidgetId, COLOR_SOURCE), null)),
            timeFormat = WidgetTimeFormat.fromStored(prefs.getString(key(appWidgetId, TIME_FORMAT), null)),
            showCountdown = prefs.getBoolean(key(appWidgetId, SHOW_COUNTDOWN), true),
            showRoom = prefs.getBoolean(key(appWidgetId, SHOW_ROOM), true),
            showNext = prefs.getBoolean(key(appWidgetId, SHOW_NEXT), true),
            showProgress = prefs.getBoolean(key(appWidgetId, SHOW_PROGRESS), true),
        )
    }

    fun save(context: Context, appWidgetId: Int, options: WidgetOptions) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(key(appWidgetId, APPEARANCE), options.appearance.storedValue)
                    .putInt(key(appWidgetId, TRANSPARENCY_PERCENT), options.transparencyPercent.coerceIn(0, 100))
                    .putInt(key(appWidgetId, PANEL_TRANSPARENCY_PERCENT), options.panelTransparencyPercent.coerceIn(0, 100))
                    .putString(key(appWidgetId, COLOR_SOURCE), options.colorSource.storedValue)
                    .putString(key(appWidgetId, TIME_FORMAT), options.timeFormat.storedValue)
                    .putBoolean(key(appWidgetId, SHOW_COUNTDOWN), options.showCountdown)
                    .putBoolean(key(appWidgetId, SHOW_ROOM), options.showRoom)
                    .putBoolean(key(appWidgetId, SHOW_NEXT), options.showNext)
                    .putBoolean(key(appWidgetId, SHOW_PROGRESS), options.showProgress)
                    .remove(key(appWidgetId, LEGACY_TRANSPARENCY))
            }

        optionFlows.compute(appWidgetId) { _, flow ->
            (flow ?: MutableStateFlow(options)).also { it.value = options }
        }
    }

    fun observe(context: Context, appWidgetId: Int): StateFlow<WidgetOptions> =
        optionFlows.computeIfAbsent(appWidgetId) {
            MutableStateFlow(read(context, appWidgetId))
        }

    fun hasSavedOptions(context: Context, appWidgetId: Int): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(key(appWidgetId, APPEARANCE))

    fun remove(context: Context, appWidgetIds: IntArray) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            appWidgetIds.forEach { id ->
                listOf(
                    APPEARANCE,
                    TRANSPARENCY_PERCENT,
                    PANEL_TRANSPARENCY_PERCENT,
                    LEGACY_TRANSPARENCY,
                    COLOR_SOURCE,
                    TIME_FORMAT,
                    SHOW_COUNTDOWN,
                    SHOW_ROOM,
                    SHOW_NEXT,
                    SHOW_PROGRESS
                )
                    .forEach { field -> remove(key(id, field)) }
            }
        }
        appWidgetIds.forEach { optionFlows.remove(it) }
    }

    fun migrate(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.zip(newWidgetIds).forEach { (oldId, newId) ->
            save(context, newId, read(context, oldId))
        }
        remove(context, oldWidgetIds)
    }

    private fun key(appWidgetId: Int, field: String) = "widget_${appWidgetId}_$field"

    private fun legacyTransparencyPercent(value: String?): Int = when (value) {
        "low" -> 25
        "medium" -> 50
        "high" -> 75
        else -> 100
    }
}
