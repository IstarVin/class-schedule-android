package com.istarvin.classschedule2.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import com.istarvin.classschedule2.widget.WidgetUpdateCoordinator
import java.util.concurrent.TimeUnit

/**
 * Refreshes the shared schedule cache without tying a network request to a widget broadcast.
 * WorkManager preserves a user-initiated refresh while the device is offline and periodically
 * keeps the local copy fresh without a continuously running service.
 */
class ScheduleSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result = when (ScheduleCache.refresh(applicationContext)) {
        is ScheduleCache.RefreshResult.Updated,
        is ScheduleCache.RefreshResult.NotModified -> {
            WidgetUpdateCoordinator.updateAll(applicationContext)
            Result.success()
        }

        is ScheduleCache.RefreshResult.Failed -> {
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_MANUAL_SYNC = "aj_schedule_manual_sync"
        private const val UNIQUE_PERIODIC_SYNC = "aj_schedule_periodic_sync"
        private const val MAX_RETRY_ATTEMPTS = 3

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Coalesces repeated refresh taps into one current network request. */
        fun enqueueManual(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScheduleSyncWorker>()
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_MANUAL_SYNC,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /** A gentle sync cadence complements exact-ish class-boundary widget updates. */
        fun ensurePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC_SYNC)
        }
    }
}
