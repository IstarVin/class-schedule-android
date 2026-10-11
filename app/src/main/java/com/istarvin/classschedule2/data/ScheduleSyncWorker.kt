package com.istarvin.classschedule2.data

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
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

    override fun doWork(): Result {
        // A queued manual refresh belongs to the profile selected when it was requested.
        val profile = inputData.getString(KEY_PROFILE)?.let(ScheduleProfile::fromId)
            ?: SchedulePreferences.selected(applicationContext)
        if (WebsiteCache.refresh(applicationContext, profile)) {
            WidgetUpdateCoordinator.updateAll(applicationContext)
            applicationContext.sendBroadcast(
                Intent(ACTION_WEBSITE_UPDATED).setPackage(applicationContext.packageName)
                    .putExtra(KEY_PROFILE, profile.id),
            )
            return Result.success()
        }
        // Widget data can still refresh if an optional website asset is unavailable.
        if (ScheduleCache.refresh(applicationContext, profile) !is ScheduleCache.RefreshResult.Failed) {
            WidgetUpdateCoordinator.updateAll(applicationContext)
        }
        return if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        private const val UNIQUE_MANUAL_SYNC = "class_schedule_manual_sync"
        private const val UNIQUE_PERIODIC_SYNC = "class_schedule_periodic_sync"
        private const val MAX_RETRY_ATTEMPTS = 3
        const val KEY_PROFILE = "schedule_profile"
        const val ACTION_WEBSITE_UPDATED = "com.istarvin.classschedule2.action.WEBSITE_UPDATED"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Coalesces repeated refresh taps into one current network request. */
        fun enqueueManual(context: Context, profile: ScheduleProfile = SchedulePreferences.selected(context)) {
            val request = OneTimeWorkRequestBuilder<ScheduleSyncWorker>()
                .setConstraints(networkConstraints)
                .setInputData(workDataOf(KEY_PROFILE to profile.id))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "${UNIQUE_MANUAL_SYNC}_${profile.id}",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /** A gentle sync cadence complements exact-ish class-boundary widget updates. */
        fun ensurePeriodic(context: Context) {
            val manager = WorkManager.getInstance(context.applicationContext)
            manager.cancelUniqueWork(UNIQUE_PERIODIC_SYNC) // Migrate the old selected-profile-only job.
            ScheduleProfile.entries.forEach { profile ->
                val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(networkConstraints)
                    .setInputData(workDataOf(KEY_PROFILE to profile.id))
                    .build()
                manager.enqueueUniquePeriodicWork(
                    "${UNIQUE_PERIODIC_SYNC}_${profile.id}",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }
    }
}
