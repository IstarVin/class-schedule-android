package com.istarvin.classschedule2.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.istarvin.classschedule2.data.ScheduleSyncWorker

/** Entry point required by the home-screen host. Heavy work stays in [WidgetUpdateCoordinator]. */
class AjScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdateCoordinator.update(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        // Hosts invoke this after resize; automatic style selection is recalculated here.
        WidgetUpdateCoordinator.update(context, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPreferences.remove(context, appWidgetIds)
        WidgetUpdateCoordinator.updateAll(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        WidgetPreferences.migrate(context, oldWidgetIds, newWidgetIds)
        WidgetUpdateCoordinator.update(context, newWidgetIds)
    }

    override fun onEnabled(context: Context) {
        ScheduleSyncWorker.ensurePeriodic(context)
        WidgetUpdateCoordinator.updateAll(context)
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshScheduler.cancel(context.applicationContext)
        ScheduleSyncWorker.cancelPeriodic(context)
    }
}
