package com.istarvin.classschedule2.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import com.istarvin.classschedule2.data.ScheduleSyncWorker

internal data class WidgetProviderEntry(
    val providerClass: Class<out AppWidgetProvider>,
    val layout: WidgetLayout,
)

internal object WidgetProviderRegistry {
    val entries = listOf(
        WidgetProviderEntry(AjScheduleWidgetProvider::class.java, WidgetLayout.MEDIUM),
        WidgetProviderEntry(AjScheduleCompactWidgetProvider::class.java, WidgetLayout.COMPACT),
        WidgetProviderEntry(AjScheduleLargeWidgetProvider::class.java, WidgetLayout.LARGE),
    )

    fun layoutFor(provider: ComponentName?): WidgetLayout? =
        entries.firstOrNull { it.providerClass.name == provider?.className }?.layout

    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return entries.any { entry ->
            manager.getAppWidgetIds(ComponentName(context, entry.providerClass)).isNotEmpty()
        }
    }
}

/** Shared lifecycle for the selectable widget types. Heavy work stays in [WidgetUpdateCoordinator]. */
open class AjScheduleWidgetProvider : AppWidgetProvider() {
    protected open val widgetLayout: WidgetLayout = WidgetLayout.MEDIUM

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdateCoordinator.update(context, appWidgetIds, widgetLayout)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        WidgetUpdateCoordinator.update(context, intArrayOf(appWidgetId), widgetLayout)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPreferences.remove(context, appWidgetIds)
        WidgetUpdateCoordinator.updateAll(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        WidgetPreferences.migrate(context, oldWidgetIds, newWidgetIds)
        WidgetUpdateCoordinator.update(context, newWidgetIds, widgetLayout)
    }

    override fun onEnabled(context: Context) {
        ScheduleSyncWorker.ensurePeriodic(context)
        WidgetUpdateCoordinator.updateAll(context)
    }

    override fun onDisabled(context: Context) {
        if (!WidgetProviderRegistry.hasWidgets(context.applicationContext)) {
            WidgetRefreshScheduler.cancel(context.applicationContext)
            ScheduleSyncWorker.cancelPeriodic(context)
        }
    }
}

class AjScheduleCompactWidgetProvider : AjScheduleWidgetProvider() {
    override val widgetLayout: WidgetLayout = WidgetLayout.COMPACT
}

class AjScheduleLargeWidgetProvider : AjScheduleWidgetProvider() {
    override val widgetLayout: WidgetLayout = WidgetLayout.LARGE
}
