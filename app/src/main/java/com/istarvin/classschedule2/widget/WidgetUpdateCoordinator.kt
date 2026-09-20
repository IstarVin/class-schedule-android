package com.istarvin.classschedule2.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.istarvin.classschedule2.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

/** Builds every RemoteViews variant from one cache snapshot, so all widget types agree on "now". */
internal object WidgetUpdateCoordinator {
    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val activeProviders = WidgetProviderRegistry.entries.mapNotNull { entry ->
            manager.getAppWidgetIds(ComponentName(appContext, entry.providerClass))
                .takeIf { it.isNotEmpty() }
                ?.let { ids -> entry to ids }
        }
        if (activeProviders.isEmpty()) {
            WidgetRefreshScheduler.cancel(appContext)
            return
        }
        val snapshot = snapshotFor(WidgetScheduleReader.load(appContext), ZonedDateTime.now())
        activeProviders.forEach { (entry, ids) ->
            if (entry.usesGlance) {
                requestGlanceCompactUpdate(appContext)
            } else {
                update(appContext, manager, ids, entry.layout, snapshot)
            }
        }
        WidgetRefreshScheduler.schedule(appContext, snapshot.refreshAt)
    }

    fun update(context: Context, appWidgetIds: IntArray, layout: WidgetLayout) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val snapshot = snapshotFor(WidgetScheduleReader.load(appContext), ZonedDateTime.now())
        val glanceIds = appWidgetIds.filter { id ->
            WidgetProviderRegistry.entryFor(manager.getAppWidgetInfo(id)?.provider)?.usesGlance == true
        }.toIntArray()
        val remoteViewIds = appWidgetIds.filterNot { id -> glanceIds.contains(id) }.toIntArray()
        if (remoteViewIds.isNotEmpty()) {
            update(appContext, manager, remoteViewIds, layout, snapshot)
        }
        if (glanceIds.isNotEmpty()) {
            requestGlanceCompactUpdate(appContext)
        }
        WidgetRefreshScheduler.schedule(appContext, snapshot.refreshAt)
    }

    private fun update(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        layout: WidgetLayout,
        snapshot: WidgetSnapshot,
    ) {
        appWidgetIds.forEach { id ->
            val preferences = WidgetPreferences.read(context, id)
            manager.updateAppWidget(id, WidgetRemoteViews.create(context, id, layout, preferences, snapshot))
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
            // A 15-minute update keeps the optional RemoteViews progress bar useful without a
            // battery-expensive per-minute alarm.
            val quarterHour = now.truncatedTo(ChronoUnit.MINUTES)
                .plusMinutes((15 - (now.minute % 15)).toLong())
            return if (quarterHour.isBefore(end)) quarterHour else end
        }
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val nextStart = next?.let { at(it.date, it.scheduleClass.startMinute, now) }
        // A date change can alter the state even if Monday's first class is days away.
        if (nextStart != null && nextStart.isBefore(midnight)) {
            // Keep the calm countdown label fresh as it crosses hour/quarter-hour boundaries.
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

private object WidgetRemoteViews {
    fun create(
        context: Context,
        appWidgetId: Int,
        layout: WidgetLayout,
        options: WidgetOptions,
        snapshot: WidgetSnapshot,
    ): RemoteViews {
        val layoutId = when (layout) {
            WidgetLayout.COMPACT -> R.layout.widget_aj_schedule_compact
            WidgetLayout.MEDIUM -> R.layout.widget_aj_schedule_medium
            WidgetLayout.LARGE -> R.layout.widget_aj_schedule_large
        }
        return RemoteViews(context.packageName, layoutId).also { views ->
            applyAppearance(context, views, layout, options)
            when (layout) {
                WidgetLayout.COMPACT -> bindCompact(context, views, options, snapshot)
                WidgetLayout.MEDIUM -> bindMedium(context, views, options, snapshot)
                WidgetLayout.LARGE -> bindLarge(context, views, options, snapshot)
            }
            bindActions(context, views, appWidgetId, layout)
        }
    }

    private fun bindCompact(
        context: Context,
        views: RemoteViews,
        options: WidgetOptions,
        snapshot: WidgetSnapshot,
    ) {
        val primary = snapshot.current ?: snapshot.next?.scheduleClass
        val isCurrent = snapshot.current != null
        val colors = palette(context, options)
        views.setTextColor(R.id.compact_state, colors.accent)
        views.setTextColor(R.id.compact_code, colors.accent)
        views.setTextColor(R.id.compact_section, colors.secondary)
        views.setTextColor(R.id.compact_subject, colors.primary)
        views.setTextColor(R.id.compact_time, colors.secondary)
        views.setTextColor(R.id.compact_countdown, colors.accent)
        views.setTextColor(R.id.compact_next, colors.secondary)
        views.setTextColor(R.id.compact_refresh, colors.accent)

        if (primary == null) {
            views.setTextViewText(R.id.compact_state, "NO SCHEDULE")
            views.setTextViewText(R.id.compact_code, "CLASS SCHEDULE")
            views.setViewVisibility(R.id.compact_section, View.GONE)
            views.setTextViewText(R.id.compact_subject, "Open your schedule")
            views.setTextViewText(R.id.compact_time, "Add classes to see what is next")
            views.setViewVisibility(R.id.compact_countdown, View.GONE)
            views.setViewVisibility(R.id.compact_next, View.GONE)
            return
        }

        views.setTextViewText(
            R.id.compact_state,
            primaryLabel(snapshot),
        )
        views.setTextViewText(R.id.compact_code, primary.code)
        setOptionalText(views, R.id.compact_section, primary.section)
        views.setTextViewText(R.id.compact_subject, primary.name)
        views.setTextViewText(
            R.id.compact_time,
            if (isCurrent) range(context, primary, options) else dateTime(context, snapshot.next!!, options, snapshot.now.toLocalDate()),
        )
        val target = if (isCurrent) {
            WidgetUpdateCoordinator.at(snapshot.now.toLocalDate(), primary.endMinute, snapshot.now)
        } else {
            snapshot.next?.let { WidgetUpdateCoordinator.at(it.date, it.scheduleClass.startMinute, snapshot.now) }
        }
        configureCountdown(
            views = views,
            viewId = R.id.compact_countdown,
            now = snapshot.now,
            target = target,
            enabled = options.showCountdown && (isCurrent || snapshot.next?.date == snapshot.now.toLocalDate()),
            prefix = if (isCurrent) "Ends in" else "Starts in",
        )
        val nextLine = when {
            !options.showNext -> null
            isCurrent -> snapshot.next?.let { "Next · ${it.scheduleClass.code} · ${dateTime(context, it, options, snapshot.now.toLocalDate())}" }
            else -> snapshot.following?.let { "Then · ${it.scheduleClass.code} · ${dateTime(context, it, options, snapshot.now.toLocalDate())}" }
        }
        setOptionalText(views, R.id.compact_next, nextLine)
    }

    private fun bindMedium(
        context: Context,
        views: RemoteViews,
        options: WidgetOptions,
        snapshot: WidgetSnapshot,
    ) {
        val colors = palette(context, options)
        val primary = snapshot.current ?: snapshot.next?.scheduleClass
        val secondary = if (snapshot.current != null) snapshot.next else snapshot.following
        listOf(
            R.id.medium_primary_label,
            R.id.medium_primary_time,
            R.id.medium_primary_room,
            R.id.medium_primary_section,
            R.id.medium_next_label,
            R.id.medium_next_time,
            R.id.medium_next_room,
            R.id.medium_next_section,
        ).forEach { views.setTextColor(it, colors.secondary) }
        listOf(R.id.medium_primary_code, R.id.medium_next_code, R.id.medium_countdown).forEach {
            views.setTextColor(it, colors.accent)
        }
        views.setTextColor(R.id.medium_refresh, colors.accent)
        listOf(R.id.medium_primary_subject, R.id.medium_next_subject).forEach {
            views.setTextColor(it, colors.primary)
        }

        if (primary == null) {
            views.setTextViewText(R.id.medium_primary_label, "NO SCHEDULE")
            views.setTextViewText(R.id.medium_primary_code, "CLASS SCHEDULE")
            views.setViewVisibility(R.id.medium_primary_section, View.GONE)
            views.setTextViewText(R.id.medium_primary_subject, "Open your schedule")
            views.setTextViewText(R.id.medium_primary_time, "Your saved schedule will appear here")
            views.setViewVisibility(R.id.medium_primary_room, View.GONE)
            views.setViewVisibility(R.id.medium_countdown, View.GONE)
            views.setViewVisibility(R.id.medium_progress, View.GONE)
            views.setViewVisibility(R.id.medium_next_panel, View.GONE)
            views.setViewVisibility(R.id.medium_divider, View.GONE)
            return
        }

        val isCurrent = snapshot.current != null
        views.setTextViewText(
            R.id.medium_primary_label,
            primaryLabel(snapshot),
        )
        views.setTextViewText(R.id.medium_primary_code, primary.code)
        setOptionalText(views, R.id.medium_primary_section, primary.section)
        views.setTextViewText(R.id.medium_primary_subject, primary.name)
        views.setTextViewText(
            R.id.medium_primary_time,
            if (isCurrent) range(context, primary, options) else dateTime(context, snapshot.next!!, options, snapshot.now.toLocalDate()),
        )
        setOptionalText(views, R.id.medium_primary_room, room(primary.room, options))
        val target = if (isCurrent) {
            WidgetUpdateCoordinator.at(snapshot.now.toLocalDate(), primary.endMinute, snapshot.now)
        } else {
            snapshot.next?.let { WidgetUpdateCoordinator.at(it.date, it.scheduleClass.startMinute, snapshot.now) }
        }
        configureCountdown(
            views,
            R.id.medium_countdown,
            snapshot.now,
            target,
            options.showCountdown && (isCurrent || snapshot.next?.date == snapshot.now.toLocalDate()),
            if (isCurrent) "Ends in" else "Starts in",
        )
        if (isCurrent && options.showProgress) {
            val elapsed = (snapshot.now.hour * 60 + snapshot.now.minute) - primary.startMinute
            val percent = (elapsed * 100 / max(1, primary.endMinute - primary.startMinute)).coerceIn(0, 100)
            views.setProgressBar(R.id.medium_progress, 100, percent, false)
            views.setViewVisibility(R.id.medium_progress, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.medium_progress, View.GONE)
        }

        if (!options.showNext || secondary == null) {
            views.setViewVisibility(R.id.medium_next_panel, View.GONE)
            views.setViewVisibility(R.id.medium_divider, View.GONE)
        } else {
            views.setViewVisibility(R.id.medium_next_panel, View.VISIBLE)
            views.setViewVisibility(R.id.medium_divider, View.VISIBLE)
            views.setTextViewText(R.id.medium_next_label, if (isCurrent) "NEXT" else "THEN")
            views.setTextViewText(R.id.medium_next_code, secondary.scheduleClass.code)
            setOptionalText(views, R.id.medium_next_section, secondary.scheduleClass.section)
            views.setTextViewText(R.id.medium_next_subject, secondary.scheduleClass.name)
            views.setTextViewText(R.id.medium_next_time, dateTime(context, secondary, options, snapshot.now.toLocalDate()))
            setOptionalText(views, R.id.medium_next_room, room(secondary.scheduleClass.room, options))
        }
    }

    private fun bindLarge(
        context: Context,
        views: RemoteViews,
        options: WidgetOptions,
        snapshot: WidgetSnapshot,
    ) {
        val colors = palette(context, options)
        val locale = Locale.getDefault()
        views.setTextColor(R.id.large_date, colors.primary)
        views.setTextColor(R.id.large_overline, colors.accent)
        views.setTextColor(R.id.large_status, colors.secondary)
        views.setTextColor(R.id.large_empty, colors.secondary)
        views.setTextColor(R.id.large_refresh, colors.accent)
        views.setTextColor(R.id.large_more, colors.secondary)
        listOf(
            R.id.large_up_next_label,
            R.id.large_up_next_time,
            R.id.large_up_next_room,
            R.id.large_up_next_following,
        ).forEach { views.setTextColor(it, colors.secondary) }
        views.setTextColor(R.id.large_up_next_label, colors.accent)
        views.setTextColor(R.id.large_up_next_subject, colors.primary)
        views.setTextColor(R.id.large_up_next_countdown, colors.accent)
        views.setTextViewText(
            R.id.large_date,
            snapshot.now.format(DateTimeFormatter.ofPattern("EEEE · MMM d", locale)).uppercase(locale),
        )
        views.setTextViewText(R.id.large_status, todaySummary(snapshot))
        if (snapshot.today.isEmpty()) {
            views.setViewVisibility(R.id.large_empty, View.VISIBLE)
            views.setTextViewText(
                R.id.large_empty,
                snapshot.next?.let { "Next · ${it.scheduleClass.name} · ${dateTime(context, it, options, snapshot.now.toLocalDate())}" }
                    ?: "Open Class Schedule to add your classes",
            )
        } else {
            views.setViewVisibility(R.id.large_empty, View.GONE)
        }

        val rowIds = intArrayOf(R.id.large_row_1, R.id.large_row_2, R.id.large_row_3, R.id.large_row_4, R.id.large_row_5)
        val startTimeIds = intArrayOf(R.id.large_start_time_1, R.id.large_start_time_2, R.id.large_start_time_3, R.id.large_start_time_4, R.id.large_start_time_5)
        val endTimeIds = intArrayOf(R.id.large_end_time_1, R.id.large_end_time_2, R.id.large_end_time_3, R.id.large_end_time_4, R.id.large_end_time_5)
        val codeIds = intArrayOf(R.id.large_code_1, R.id.large_code_2, R.id.large_code_3, R.id.large_code_4, R.id.large_code_5)
        val sectionIds = intArrayOf(R.id.large_section_1, R.id.large_section_2, R.id.large_section_3, R.id.large_section_4, R.id.large_section_5)
        val subjectIds = intArrayOf(R.id.large_subject_1, R.id.large_subject_2, R.id.large_subject_3, R.id.large_subject_4, R.id.large_subject_5)
        val roomIds = intArrayOf(R.id.large_room_1, R.id.large_room_2, R.id.large_room_3, R.id.large_room_4, R.id.large_room_5)
        val nowIds = intArrayOf(R.id.large_now_1, R.id.large_now_2, R.id.large_now_3, R.id.large_now_4, R.id.large_now_5)
        val statusBarIds = intArrayOf(R.id.large_status_bar_1, R.id.large_status_bar_2, R.id.large_status_bar_3, R.id.large_status_bar_4, R.id.large_status_bar_5)
        snapshot.today.take(5).forEachIndexed { index, item ->
            val isCurrent = snapshot.current?.let { it.startMinute == item.startMinute && it.code == item.code } == true
            val isPast = !isCurrent && snapshot.now.hour * 60 + snapshot.now.minute >= item.endMinute
            val isFirstUpcoming = !isPast && !isCurrent && snapshot.next?.let {
                it.date == snapshot.now.toLocalDate() &&
                    it.scheduleClass.startMinute == item.startMinute &&
                    it.scheduleClass.code == item.code
            } == true
            views.setViewVisibility(rowIds[index], View.VISIBLE)
            views.setTextViewText(startTimeIds[index], time(context, item.startMinute, options))
            views.setTextViewText(endTimeIds[index], time(context, item.endMinute, options))
            views.setTextViewText(codeIds[index], item.code)
            setOptionalText(views, sectionIds[index], item.section)
            views.setTextViewText(subjectIds[index], item.name)
            setOptionalText(views, roomIds[index], room(item.room, options))
            views.setViewVisibility(nowIds[index], if (isCurrent) View.VISIBLE else View.GONE)
            views.setTextColor(codeIds[index], if (isPast) colors.muted else colors.accent)
            views.setTextColor(sectionIds[index], if (isPast) colors.muted else colors.secondary)
            views.setTextColor(startTimeIds[index], if (isPast) colors.muted else colors.secondary)
            views.setTextColor(endTimeIds[index], colors.muted)
            views.setTextColor(subjectIds[index], if (isPast) colors.muted else colors.primary)
            views.setTextColor(roomIds[index], if (isPast) colors.muted else colors.secondary)
            views.setTextColor(nowIds[index], colors.accent)
            views.setViewVisibility(statusBarIds[index], View.VISIBLE)
            views.setInt(
                statusBarIds[index],
                "setBackgroundColor",
                when {
                    isCurrent -> colors.accent
                    isFirstUpcoming -> colors.accent
                    isPast -> colors.muted
                    else -> colors.secondary
                },
            )
            views.setInt(
                rowIds[index],
                "setBackgroundResource",
                if (isCurrent) activeRowBackground(context, options) else panelBackground(context, options),
            )
        }
        for (index in snapshot.today.size.coerceAtMost(5) until rowIds.size) {
            views.setViewVisibility(rowIds[index], View.GONE)
        }
        if (snapshot.today.size > 5) {
            views.setTextViewText(R.id.large_more, "+${snapshot.today.size - 5} more classes")
            views.setViewVisibility(R.id.large_more, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.large_more, View.GONE)
        }

        val next = snapshot.next
        if (next == null) {
            views.setViewVisibility(R.id.large_up_next_panel, View.GONE)
        } else {
            views.setViewVisibility(R.id.large_up_next_panel, View.VISIBLE)
            views.setTextViewText(R.id.large_up_next_label, "UP NEXT")
            views.setTextViewText(R.id.large_up_next_subject, next.scheduleClass.name)
            views.setTextViewText(
                R.id.large_up_next_time,
                if (next.date == snapshot.now.toLocalDate()) {
                    time(context, next.scheduleClass.startMinute, options)
                } else {
                    dateTime(context, next, options, snapshot.now.toLocalDate())
                },
            )
            setOptionalText(views, R.id.large_up_next_room, room(next.scheduleClass.room, options))
            configureCountdown(
                views = views,
                viewId = R.id.large_up_next_countdown,
                now = snapshot.now,
                target = WidgetUpdateCoordinator.at(next.date, next.scheduleClass.startMinute, snapshot.now),
                enabled = options.showCountdown && next.date == snapshot.now.toLocalDate(),
                prefix = "Starts in",
            )
            setOptionalText(
                views,
                R.id.large_up_next_following,
                snapshot.following?.let {
                    "Then · ${it.scheduleClass.code} · ${dateTime(context, it, options, snapshot.now.toLocalDate())}"
                },
            )
        }
    }

    private fun applyAppearance(context: Context, views: RemoteViews, layout: WidgetLayout, options: WidgetOptions) {
        val background = rootBackground(context, options)
        val panel = panelBackground(context, options)
        when (layout) {
            WidgetLayout.COMPACT -> {
                views.setInt(R.id.compact_root, "setBackgroundResource", background)
                views.setInt(R.id.compact_state, "setBackgroundResource", panel)
                views.setInt(R.id.compact_refresh, "setBackgroundResource", panel)
            }
            WidgetLayout.MEDIUM -> {
                views.setInt(R.id.medium_root, "setBackgroundResource", background)
                views.setInt(R.id.medium_primary_panel, "setBackgroundResource", panel)
                views.setInt(R.id.medium_next_panel, "setBackgroundResource", panel)
                // The refresh control sits inside a panel, so use the root surface to retain a
                // visible, compact touch target in every color mode.
                views.setInt(R.id.medium_refresh, "setBackgroundResource", background)
            }
            WidgetLayout.LARGE -> {
                views.setInt(R.id.large_root, "setBackgroundResource", background)
                views.setInt(R.id.large_refresh, "setBackgroundResource", panel)
            }
        }
    }

    private fun bindActions(context: Context, views: RemoteViews, appWidgetId: Int, layout: WidgetLayout) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launch.data = "aj-schedule://widget/$appWidgetId/open".toUri()
        val openApp = PendingIntent.getActivity(
            context,
            appWidgetId,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val refresh = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            Intent(context, WidgetRefreshReceiver::class.java)
                .setAction(WidgetRefreshReceiver.ACTION_MANUAL_REFRESH)
                .setData("aj-schedule://widget/$appWidgetId/refresh".toUri()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        when (layout) {
            WidgetLayout.COMPACT -> {
                views.setOnClickPendingIntent(R.id.compact_root, openApp)
                views.setOnClickPendingIntent(R.id.compact_refresh, refresh)
            }
            WidgetLayout.MEDIUM -> {
                views.setOnClickPendingIntent(R.id.medium_root, openApp)
                views.setOnClickPendingIntent(R.id.medium_refresh, refresh)
            }
            WidgetLayout.LARGE -> {
                views.setOnClickPendingIntent(R.id.large_root, openApp)
                views.setOnClickPendingIntent(R.id.large_refresh, refresh)
            }
        }
    }

    private fun configureCountdown(
        views: RemoteViews,
        viewId: Int,
        now: ZonedDateTime,
        target: ZonedDateTime?,
        enabled: Boolean,
        prefix: String,
    ) {
        if (!enabled || target == null || !target.isAfter(now)) {
            views.setViewVisibility(viewId, View.GONE)
            return
        }
        val remaining = Duration.between(now, target).toMillis()
        // A static minute-level label is easier to scan than a live HH:MM:SS timer. The widget
        // refresh alarm updates this label as the class approaches, while the final minute is
        // intentionally still calm instead of introducing a second-by-second countdown.
        val minutes = (remaining + 59_999L) / 60_000L
        val duration = when {
            minutes < 1 -> "less than 1 min"
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0L -> "${minutes / 60} hr"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
        views.setTextViewText(viewId, "$prefix $duration")
        views.setViewVisibility(viewId, View.VISIBLE)
    }

    private fun setOptionalText(views: RemoteViews, viewId: Int, text: String?) {
        if (text.isNullOrBlank()) {
            views.setViewVisibility(viewId, View.GONE)
        } else {
            views.setTextViewText(viewId, text)
            views.setViewVisibility(viewId, View.VISIBLE)
        }
    }

    private fun todaySummary(snapshot: WidgetSnapshot): String = when {
        snapshot.today.isEmpty() -> "No classes today"
        snapshot.today.size == 1 -> "1 class today"
        else -> "${snapshot.today.size} classes today"
    }

    private fun primaryLabel(snapshot: WidgetSnapshot): String = when (snapshot.state) {
        WidgetState.CURRENT -> if (isLastClassToday(snapshot)) "LAST CLASS TODAY" else "NOW"
        WidgetState.UP_NEXT -> "UP NEXT"
        WidgetState.DONE_FOR_TODAY -> "DONE FOR TODAY"
        WidgetState.NO_CLASSES_TODAY -> "NO CLASSES TODAY"
        WidgetState.EMPTY -> "NO SCHEDULE"
    }

    private fun isLastClassToday(snapshot: WidgetSnapshot): Boolean =
        snapshot.current != null && snapshot.next?.date != snapshot.now.toLocalDate()

    private fun room(room: String, options: WidgetOptions): String? =
        room.takeIf { options.showRoom && it.isNotBlank() }?.let { "Room $it" }

    private fun range(context: Context, item: ScheduleClass, options: WidgetOptions): String {
        val use24Hour = when (options.timeFormat) {
            WidgetTimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
            WidgetTimeFormat.HOUR_12 -> false
            WidgetTimeFormat.HOUR_24 -> true
        }
        if (use24Hour) return "${time(context, item.startMinute, options)}–${time(context, item.endMinute, options)}"

        val start = localTime(item.startMinute)
        val end = localTime(item.endMinute)
        val startMeridiem = start.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
        val endMeridiem = end.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
        val clock = DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())
        return if (startMeridiem == endMeridiem) {
            "${start.format(clock)}–${end.format(clock)} $endMeridiem"
        } else {
            "${start.format(clock)} $startMeridiem–${end.format(clock)} $endMeridiem"
        }
    }

    private fun localTime(minute: Int): LocalTime =
        if (minute == 1440) LocalTime.MIDNIGHT else LocalTime.of(minute / 60, minute % 60)

    private fun dateTime(
        context: Context,
        scheduled: ScheduledClass,
        options: WidgetOptions,
        today: LocalDate,
    ): String {
        val prefix = when (scheduled.date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> scheduled.date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        }
        return "$prefix · ${time(context, scheduled.scheduleClass.startMinute, options)}"
    }

    private fun time(context: Context, minute: Int, options: WidgetOptions): String {
        val use24Hour = when (options.timeFormat) {
            WidgetTimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
            WidgetTimeFormat.HOUR_12 -> false
            WidgetTimeFormat.HOUR_24 -> true
        }
        if (minute == 1440) return if (use24Hour) "00:00" else "12:00 AM"
        return localTime(minute).format(
            DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()),
        )
    }

    private data class Palette(val primary: Int, val secondary: Int, val muted: Int, val accent: Int)

    private fun palette(context: Context, options: WidgetOptions): Palette {
        if (options.colorSource == WidgetColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dark = isDark(context, options.appearance)
            return Palette(
                primary = context.getColor(if (dark) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900),
                secondary = context.getColor(if (dark) android.R.color.system_neutral1_200 else android.R.color.system_neutral1_700),
                muted = context.getColor(if (dark) android.R.color.system_neutral1_400 else android.R.color.system_neutral1_500),
                accent = context.getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600),
            )
        }
        val resources = when (options.appearance) {
            WidgetAppearance.SYSTEM -> intArrayOf(
                R.color.widget_text_primary,
                R.color.widget_text_secondary,
                R.color.widget_text_muted,
                R.color.widget_accent,
            )
            WidgetAppearance.LIGHT -> intArrayOf(
                R.color.widget_light_text_primary,
                R.color.widget_light_text_secondary,
                R.color.widget_light_text_muted,
                R.color.widget_light_accent,
            )
            WidgetAppearance.DARK -> intArrayOf(
                R.color.widget_dark_text_primary,
                R.color.widget_dark_text_secondary,
                R.color.widget_dark_text_muted,
                R.color.widget_dark_accent,
            )
        }
        return Palette(
            primary = context.getColor(resources[0]),
            secondary = context.getColor(resources[1]),
            muted = context.getColor(resources[2]),
            accent = context.getColor(resources[3]),
        )
    }

    private fun rootBackground(context: Context, options: WidgetOptions): Int {
        if (options.colorSource == WidgetColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (isDark(context, options.appearance)) R.drawable.widget_background_dynamic_dark else R.drawable.widget_background_dynamic_light
        }
        return when (options.appearance) {
            WidgetAppearance.SYSTEM -> R.drawable.widget_background
            WidgetAppearance.LIGHT -> R.drawable.widget_background_light
            WidgetAppearance.DARK -> R.drawable.widget_background_dark
        }
    }

    private fun panelBackground(context: Context, options: WidgetOptions): Int {
        if (options.colorSource == WidgetColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (isDark(context, options.appearance)) R.drawable.widget_panel_background_dynamic_dark else R.drawable.widget_panel_background_dynamic_light
        }
        return when (options.appearance) {
            WidgetAppearance.SYSTEM -> R.drawable.widget_panel_background
            WidgetAppearance.LIGHT -> R.drawable.widget_panel_background_light
            WidgetAppearance.DARK -> R.drawable.widget_panel_background_dark
        }
    }

    private fun activeRowBackground(context: Context, options: WidgetOptions): Int {
        if (options.colorSource == WidgetColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (isDark(context, options.appearance)) {
                R.drawable.widget_row_active_dynamic_dark
            } else {
                R.drawable.widget_row_active_dynamic_light
            }
        }
        return when (options.appearance) {
            WidgetAppearance.SYSTEM -> R.drawable.widget_row_active
            WidgetAppearance.LIGHT -> R.drawable.widget_row_active_light
            WidgetAppearance.DARK -> R.drawable.widget_row_active_dark
        }
    }

    private fun isDark(context: Context, appearance: WidgetAppearance): Boolean = when (appearance) {
        WidgetAppearance.DARK -> true
        WidgetAppearance.LIGHT -> false
        WidgetAppearance.SYSTEM ->
            context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
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
