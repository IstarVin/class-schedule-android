package com.istarvin.classschedule2.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.istarvin.classschedule2.data.ScheduleCache
import com.istarvin.classschedule2.data.ScheduleSyncWorker
import java.util.concurrent.Executors

/** Re-evaluates cached data after time/system changes without doing network I/O in a broadcast. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MANUAL_REFRESH) {
            // The state redraw is immediate; the constrained worker fetches fresh JSON when
            // network is available without holding this broadcast open for a network request.
            ScheduleSyncWorker.enqueueManual(context)
        }
        val pendingResult = goAsync()
        UPDATE_EXECUTOR.execute {
            try {
                WidgetUpdateCoordinator.updateAll(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MANUAL_REFRESH = "com.istarvin.classschedule2.widget.action.MANUAL_REFRESH"
        const val ACTION_SCHEDULED_REFRESH = "com.istarvin.classschedule2.widget.action.SCHEDULED_REFRESH"

        /** Mirrors [ScheduleCache.ACTION_SCHEDULE_UPDATED] so manifest declarations stay obvious. */
        const val ACTION_SCHEDULE_UPDATED = ScheduleCache.ACTION_SCHEDULE_UPDATED

        private val UPDATE_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "class-schedule-widget-update").apply { isDaemon = true }
        }
    }
}
