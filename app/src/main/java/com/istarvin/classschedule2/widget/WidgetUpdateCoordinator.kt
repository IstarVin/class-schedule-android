package com.istarvin.classschedule2.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidgetManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Coordinates cache snapshots, Glance redraws, and the shared time-boundary alarm. */
internal object WidgetUpdateCoordinator {
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun updateAll(context: Context) {
        updateScope.launch {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val activeProviders = WidgetProviderRegistry.entries.mapNotNull { entry ->
                manager.getAppWidgetIds(ComponentName(appContext, entry.providerClass))
                    .takeIf { it.isNotEmpty() }
                    ?.let { ids -> entry to ids }
            }
            if (activeProviders.isEmpty()) {
                WidgetRefreshScheduler.cancel(appContext)
                return@launch
            }

            val snapshot = withContext(Dispatchers.IO) {
                snapshotFor(WidgetScheduleReader.load(appContext), ZonedDateTime.now())
            }
            val glanceManager = GlanceAppWidgetManager(appContext)
            activeProviders.forEach { (entry, appWidgetIds) ->
                appWidgetIds.forEach { appWidgetId ->
                    WidgetSnapshotState.update(appWidgetId, snapshot)
                    updateGlanceWidget(entry, glanceManager, appContext, appWidgetId)
                }
            }
            WidgetRefreshScheduler.schedule(appContext, snapshot.refreshAt)
        }
    }

    fun update(context: Context, appWidgetIds: IntArray, layout: WidgetLayout) {
        if (appWidgetIds.isEmpty()) return
        updateScope.launch {
            val appContext = context.applicationContext
            val snapshot = withContext(Dispatchers.IO) {
                snapshotFor(WidgetScheduleReader.load(appContext), ZonedDateTime.now())
            }
            val entry = WidgetProviderRegistry.entryFor(layout) ?: return@launch
            val glanceManager = GlanceAppWidgetManager(appContext)
            appWidgetIds.forEach { appWidgetId ->
                WidgetSnapshotState.update(appWidgetId, snapshot)
                updateGlanceWidget(entry, glanceManager, appContext, appWidgetId)
            }
            WidgetRefreshScheduler.schedule(appContext, snapshot.refreshAt)
        }
    }

    private suspend fun updateGlanceWidget(
        entry: WidgetProviderEntry,
        glanceManager: GlanceAppWidgetManager,
        context: Context,
        appWidgetId: Int,
    ) {
        try {
            entry.glanceWidget.update(context, glanceManager.getGlanceIdBy(appWidgetId))
        } catch (_: IllegalArgumentException) {
            // A launcher can remove an instance between AppWidgetManager enumeration and update.
        }
    }

    /** Kept internal so local tests can cover time transitions without Android framework state. */
    internal fun snapshotFor(schedule: WidgetSchedule, now: ZonedDateTime): WidgetSnapshot {
        val today = schedule.classesOn(now.toLocalDate())
        val minuteOfDay = now.hour * 60 + now.minute
        val current = today.firstOrNull { minuteOfDay >= it.startMinute && minuteOfDay < it.endMinute }
        val next = nextAfter(
            schedule = schedule,
            date = now.toLocalDate(),
            minute = current?.endMinute ?: minuteOfDay,
            includeMinute = current != null,
        )
        val following = next?.let {
            nextAfter(schedule, it.date, it.scheduleClass.endMinute, includeMinute = true)
        }
        val state = when {
            current != null -> WidgetState.CURRENT
            next != null && next.date == now.toLocalDate() -> WidgetState.UP_NEXT
            today.isNotEmpty() -> WidgetState.DONE_FOR_TODAY
            next != null -> WidgetState.NO_CLASSES_TODAY
            else -> WidgetState.EMPTY
        }
        return WidgetSnapshot(
            now = now,
            today = today,
            current = current,
            next = next,
            following = following,
            state = state,
            refreshAt = nextRefreshAt(now, current, next),
        )
    }

    private fun nextAfter(
        schedule: WidgetSchedule,
        date: LocalDate,
        minute: Int,
        includeMinute: Boolean,
    ): ScheduledClass? {
        // One complete week is enough for a recurring weekly schedule; a second week makes a
        // sparse/holiday schedule degrade gracefully without continuously waking the device.
        for (offset in 0..13L) {
            val candidateDate = date.plusDays(offset)
            val candidate = schedule.classesOn(candidateDate).firstOrNull { item ->
                offset > 0 || item.startMinute > minute || (includeMinute && item.startMinute == minute)
            }
            if (candidate != null) return ScheduledClass(candidate, candidateDate)
        }
        return null
    }

    private fun nextRefreshAt(
        now: ZonedDateTime,
        current: ScheduleClass?,
        next: ScheduledClass?,
    ): ZonedDateTime {
        if (current != null) {
            val end = at(now.toLocalDate(), current.endMinute, now)
            // A 15-minute update keeps progress and countdown labels useful without a
            // battery-expensive per-minute alarm.
            val quarterHour = now.truncatedTo(ChronoUnit.MINUTES)
                .plusMinutes((15 - (now.minute % 15)).toLong())
            return if (quarterHour.isBefore(end)) quarterHour else end
        }
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val nextStart = next?.let { at(it.date, it.scheduleClass.startMinute, now) }
        // A date change can alter the state even if Monday's first class is days away.
        if (nextStart != null && nextStart.isBefore(midnight)) {
            val quarterHour = now.truncatedTo(ChronoUnit.MINUTES)
                .plusMinutes((15 - (now.minute % 15)).toLong())
            return if (quarterHour.isBefore(nextStart)) quarterHour else nextStart
        }
        return midnight
    }

    internal fun at(date: LocalDate, minute: Int, reference: ZonedDateTime): ZonedDateTime =
        if (minute == 1440) date.plusDays(1).atStartOfDay(reference.zone)
        else date.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(reference.zone)
}

/** Keeps active Glance compositions in sync with the latest schedule/time snapshot. */
internal object WidgetSnapshotState {
    private val snapshots = ConcurrentHashMap<Int, MutableStateFlow<WidgetSnapshot>>()

    fun observe(appWidgetId: Int, initial: WidgetSnapshot): StateFlow<WidgetSnapshot> =
        snapshots.computeIfAbsent(appWidgetId) { MutableStateFlow(initial) }.asStateFlow()

    fun update(appWidgetId: Int, snapshot: WidgetSnapshot) {
        snapshots[appWidgetId]?.value = snapshot
    }

    fun remove(appWidgetIds: IntArray) {
        appWidgetIds.forEach(snapshots::remove)
    }
}

/** One inexact alarm is shared by all widget instances; it is recalculated on every update. */
internal object WidgetRefreshScheduler {
    private const val REQUEST_CODE = 7301

    fun schedule(context: Context, refreshAt: ZonedDateTime) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val delay = max(60_000L, Duration.between(ZonedDateTime.now(), refreshAt).toMillis())
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delay,
            refreshPendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(refreshPendingIntent(context))
    }

    private fun refreshPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WidgetRefreshReceiver::class.java)
            .setAction(WidgetRefreshReceiver.ACTION_SCHEDULED_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
