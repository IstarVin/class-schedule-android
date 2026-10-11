package com.istarvin.classschedule2.widget

import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.istarvin.classschedule2.data.ScheduleSyncWorker

internal data class WidgetProviderEntry(
    val providerClass: Class<out AppWidgetProvider>,
    val layout: WidgetLayout,
    val glanceWidget: GlanceAppWidget,
)

internal object WidgetProviderRegistry {
    val entries = listOf(
        WidgetProviderEntry(
            providerClass = ClassScheduleStackedWidgetProvider::class.java,
            layout = WidgetLayout.STACKED,
            glanceWidget = ClassScheduleStackedGlanceWidget,
        ),
        WidgetProviderEntry(
            providerClass = ClassScheduleLargeWidgetProvider::class.java,
            layout = WidgetLayout.LARGE,
            glanceWidget = ClassScheduleLargeGlanceWidget,
        ),
    )

    fun entryFor(provider: ComponentName?): WidgetProviderEntry? =
        entries.firstOrNull { it.providerClass.name == provider?.className }

    fun entryFor(layout: WidgetLayout): WidgetProviderEntry? =
        entries.firstOrNull { it.layout == layout }

    fun layoutFor(provider: ComponentName?): WidgetLayout? = entryFor(provider)?.layout

    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return entries.any { entry ->
            manager.getAppWidgetIds(ComponentName(context, entry.providerClass)).isNotEmpty()
        }
    }
}

/** Shared lifecycle for the selectable Glance widget types. */
abstract class ClassScheduleGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    protected abstract val widgetLayout: WidgetLayout

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ScheduleSyncWorker.ensurePeriodic(context)
        WidgetUpdateCoordinator.updateAll(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetPreferences.remove(context, appWidgetIds)
        WidgetSnapshotState.remove(appWidgetIds)
        WidgetUpdateCoordinator.updateAll(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        WidgetPreferences.migrate(context, oldWidgetIds, newWidgetIds)
        WidgetUpdateCoordinator.update(context, newWidgetIds, widgetLayout)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!WidgetProviderRegistry.hasWidgets(context.applicationContext)) {
            WidgetRefreshScheduler.cancel(context.applicationContext)
            // Website synchronization also serves the app when no widgets remain.
        }
    }
}

class ClassScheduleLargeWidgetProvider : ClassScheduleGlanceWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClassScheduleLargeGlanceWidget
    override val widgetLayout: WidgetLayout = WidgetLayout.LARGE
}

class ClassScheduleStackedWidgetProvider : ClassScheduleGlanceWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClassScheduleStackedGlanceWidget
    override val widgetLayout: WidgetLayout = WidgetLayout.STACKED
}
