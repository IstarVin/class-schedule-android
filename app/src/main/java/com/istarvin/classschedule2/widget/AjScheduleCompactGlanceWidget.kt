package com.istarvin.classschedule2.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.istarvin.classschedule2.MainActivity
import com.istarvin.classschedule2.R
import com.istarvin.classschedule2.data.ScheduleSyncWorker
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A first Glance implementation kept beside the existing compact RemoteViews widget. */
internal object AjScheduleCompactGlanceWidget : GlanceAppWidget() {
    @SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = (id as? AppWidgetId)?.appWidgetId ?: return
        val appContext = context.applicationContext
        val (snapshot, options) = withContext(Dispatchers.IO) {
            WidgetUpdateCoordinator.snapshotFor(
                WidgetScheduleReader.load(appContext),
                ZonedDateTime.now(),
            ) to WidgetPreferences.read(appContext, appWidgetId)
        }
        WidgetRefreshScheduler.schedule(appContext, snapshot.refreshAt)
        provideContent {
            CompactGlanceContent(appContext, options, snapshot)
        }
    }
}

class AjScheduleCompactGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AjScheduleCompactGlanceWidget

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ScheduleSyncWorker.ensurePeriodic(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetPreferences.remove(context, appWidgetIds)
        stopSharedWidgetRefreshIfUnused(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopSharedWidgetRefreshIfUnused(context)
    }

    private fun stopSharedWidgetRefreshIfUnused(context: Context) {
        if (!WidgetProviderRegistry.hasWidgets(context.applicationContext)) {
            WidgetRefreshScheduler.cancel(context.applicationContext)
            ScheduleSyncWorker.cancelPeriodic(context)
        }
    }
}

private val glanceUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

internal fun requestGlanceCompactUpdate(context: Context) {
    glanceUpdateScope.launch {
        AjScheduleCompactGlanceWidget.updateAll(context.applicationContext)
    }
}

@androidx.compose.runtime.Composable
private fun CompactGlanceContent(
    context: Context,
    options: WidgetOptions,
    snapshot: WidgetSnapshot,
) {
    val colors = glancePalette(context, options)
    val openApp = actionStartActivity<MainActivity>()
    val refresh = actionSendBroadcast(
        Intent(context, WidgetRefreshReceiver::class.java)
            .setAction(WidgetRefreshReceiver.ACTION_MANUAL_REFRESH),
    )
    val rootModifier = GlanceModifier
        .fillMaxSize()
        .background(colors.surface)
        .cornerRadius(24.dp)
        .padding(14.dp)
        .clickable(openApp)

    Column(
        modifier = rootModifier,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = glanceStateLabel(context, snapshot),
                modifier = GlanceModifier
                    .background(colors.panel)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                style = TextStyle(
                    color = colors.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = context.getString(R.string.widget_glance_refresh_symbol),
                modifier = GlanceModifier
                    .size(28.dp)
                    .background(colors.panel)
                    .cornerRadius(14.dp)
                    .clickable(refresh),
                style = TextStyle(
                    color = colors.accent,
                    fontSize = 17.sp,
                    textAlign = androidx.glance.text.TextAlign.Center,
                ),
            )
        }

        val primary = snapshot.current ?: snapshot.next?.scheduleClass
        if (primary == null) {
            Spacer(GlanceModifier.height(9.dp))
            Text(
                text = context.getString(R.string.widget_glance_no_schedule_code),
                style = TextStyle(
                    color = colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = context.getString(R.string.widget_glance_no_schedule_subject),
                modifier = GlanceModifier.padding(top = 4.dp),
                style = TextStyle(
                    color = colors.primary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
            )
            Text(
                text = context.getString(R.string.widget_glance_no_schedule_time),
                modifier = GlanceModifier.padding(top = 7.dp),
                style = TextStyle(color = colors.secondary, fontSize = 12.sp),
                maxLines = 1,
            )
            return@Column
        }

        val isCurrent = snapshot.current != null
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 9.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = primary.code,
                style = TextStyle(
                    color = colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            if (primary.section.isNotBlank()) {
                Text(
                    text = primary.section,
                    modifier = GlanceModifier.padding(start = 10.dp),
                    style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
        Text(
            text = primary.name,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            style = TextStyle(
                color = colors.primary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
        )

        val timeText = if (isCurrent) {
            timeRange(context, primary, options)
        } else {
            dateTime(context, snapshot.next!!, options, snapshot.now.toLocalDate())
        }
        val room = primary.room.takeIf { options.showRoom && it.isNotBlank() }
            ?.let { context.getString(R.string.widget_glance_room, it) }
        Text(
            text = listOfNotNull(timeText, room).joinToString(" · "),
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            style = TextStyle(color = colors.secondary, fontSize = 12.sp),
            maxLines = 1,
        )

        val target = if (isCurrent) {
            WidgetUpdateCoordinator.at(snapshot.now.toLocalDate(), primary.endMinute, snapshot.now)
        } else {
            snapshot.next?.let {
                WidgetUpdateCoordinator.at(it.date, it.scheduleClass.startMinute, snapshot.now)
            }
        }
        countdownText(context, snapshot, options, target, isCurrent)?.let { countdown ->
            Text(
                text = countdown,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                style = TextStyle(
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }

        val nextLine = when {
            !options.showNext -> null
            isCurrent -> snapshot.next?.let {
                context.getString(
                    R.string.widget_glance_next_line,
                    it.scheduleClass.code,
                    dateTime(context, it, options, snapshot.now.toLocalDate()),
                )
            }
            else -> snapshot.following?.let {
                context.getString(
                    R.string.widget_glance_then_line,
                    it.scheduleClass.code,
                    dateTime(context, it, options, snapshot.now.toLocalDate()),
                )
            }
        }
        nextLine?.let {
            Text(
                text = it,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

private fun glanceStateLabel(context: Context, snapshot: WidgetSnapshot): String = when (snapshot.state) {
    WidgetState.CURRENT -> context.getString(
        if (snapshot.next?.date != snapshot.now.toLocalDate()) {
            R.string.widget_glance_state_last_class
        } else {
            R.string.widget_glance_state_current
        },
    )
    WidgetState.UP_NEXT -> context.getString(R.string.widget_glance_state_up_next)
    WidgetState.DONE_FOR_TODAY -> context.getString(R.string.widget_glance_state_done)
    WidgetState.NO_CLASSES_TODAY -> context.getString(R.string.widget_glance_state_no_classes)
    WidgetState.EMPTY -> context.getString(R.string.widget_glance_state_no_schedule)
}

private fun countdownText(
    context: Context,
    snapshot: WidgetSnapshot,
    options: WidgetOptions,
    target: ZonedDateTime?,
    isCurrent: Boolean,
): String? {
    if (!options.showCountdown || target == null || !target.isAfter(snapshot.now)) return null
    if (!isCurrent && snapshot.next?.date != snapshot.now.toLocalDate()) return null
    val minutes = (Duration.between(snapshot.now, target).toMillis() + 59_999L) / 60_000L
    val duration = when {
        minutes < 1 -> context.getString(R.string.widget_glance_less_than_minute)
        minutes < 60 -> context.getString(R.string.widget_glance_minutes, minutes)
        minutes % 60 == 0L -> context.getString(R.string.widget_glance_hours, minutes / 60)
        else -> context.getString(R.string.widget_glance_hours_minutes, minutes / 60, minutes % 60)
    }
    return context.getString(
        if (isCurrent) R.string.widget_glance_ends_in else R.string.widget_glance_starts_in,
        duration,
    )
}

private fun timeRange(context: Context, item: ScheduleClass, options: WidgetOptions): String {
    val start = localTime(item.startMinute)
    val end = localTime(item.endMinute)
    if (uses24HourClock(context, options)) {
        return "${time(context, item.startMinute, options)}–${time(context, item.endMinute, options)}"
    }
    val meridiem = DateTimeFormatter.ofPattern("a", Locale.getDefault())
    val clock = DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())
    val startMeridiem = start.format(meridiem)
    val endMeridiem = end.format(meridiem)
    return if (startMeridiem == endMeridiem) {
        "${start.format(clock)}–${end.format(clock)} $endMeridiem"
    } else {
        "${start.format(clock)} $startMeridiem–${end.format(clock)} $endMeridiem"
    }
}

private fun dateTime(
    context: Context,
    scheduled: ScheduledClass,
    options: WidgetOptions,
    today: LocalDate,
): String {
    val prefix = when (scheduled.date) {
        today -> context.getString(R.string.widget_glance_today)
        today.plusDays(1) -> context.getString(R.string.widget_glance_tomorrow)
        else -> scheduled.date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
    }
    return "$prefix · ${time(context, scheduled.scheduleClass.startMinute, options)}"
}

private fun time(context: Context, minute: Int, options: WidgetOptions): String {
    if (minute == 1440) {
        return context.getString(
            if (uses24HourClock(context, options)) {
                R.string.widget_glance_midnight_24
            } else {
                R.string.widget_glance_midnight_12
            },
        )
    }
    return localTime(minute).format(
        DateTimeFormatter.ofPattern(
            if (uses24HourClock(context, options)) "HH:mm" else "h:mm a",
            Locale.getDefault(),
        ),
    )
}

private fun uses24HourClock(context: Context, options: WidgetOptions): Boolean = when (options.timeFormat) {
    WidgetTimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
    WidgetTimeFormat.HOUR_12 -> false
    WidgetTimeFormat.HOUR_24 -> true
}

private fun localTime(minute: Int): LocalTime =
    if (minute == 1440) LocalTime.MIDNIGHT else LocalTime.of(minute / 60, minute % 60)

private data class GlancePalette(
    val surface: ColorProvider,
    val panel: ColorProvider,
    val primary: ColorProvider,
    val secondary: ColorProvider,
    val accent: ColorProvider,
)

private fun glancePalette(context: Context, options: WidgetOptions): GlancePalette {
    val dark = isDark(context, options.appearance)
    val dynamic = options.colorSource == WidgetColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val surface = if (dynamic) {
        context.getColor(if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50)
    } else {
        context.getColor(
            when (options.appearance) {
                WidgetAppearance.SYSTEM -> R.color.widget_surface
                WidgetAppearance.LIGHT -> R.color.widget_light_surface
                WidgetAppearance.DARK -> R.color.widget_dark_surface
            },
        )
    }
    val panel = if (dynamic) {
        context.getColor(if (dark) android.R.color.system_neutral1_800 else android.R.color.system_neutral1_100)
    } else {
        context.getColor(
            when (options.appearance) {
                WidgetAppearance.SYSTEM -> R.color.widget_panel
                WidgetAppearance.LIGHT -> R.color.widget_light_panel
                WidgetAppearance.DARK -> R.color.widget_dark_panel
            },
        )
    }
    val textResources = if (dynamic) {
        intArrayOf(
            if (dark) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900,
            if (dark) android.R.color.system_neutral1_200 else android.R.color.system_neutral1_700,
            if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600,
        )
    } else {
        when (options.appearance) {
            WidgetAppearance.SYSTEM -> intArrayOf(
                R.color.widget_text_primary,
                R.color.widget_text_secondary,
                R.color.widget_accent,
            )
            WidgetAppearance.LIGHT -> intArrayOf(
                R.color.widget_light_text_primary,
                R.color.widget_light_text_secondary,
                R.color.widget_light_accent,
            )
            WidgetAppearance.DARK -> intArrayOf(
                R.color.widget_dark_text_primary,
                R.color.widget_dark_text_secondary,
                R.color.widget_dark_accent,
            )
        }
    }
    return GlancePalette(
        surface = glanceColor(surface),
        panel = glanceColor(panel),
        primary = glanceColor(context.getColor(textResources[0])),
        secondary = glanceColor(context.getColor(textResources[1])),
        accent = glanceColor(context.getColor(textResources[2])),
    )
}

private fun glanceColor(color: Int): ColorProvider = FixedGlanceColorProvider(
    ComposeColor(
        red = AndroidColor.red(color) / 255f,
        green = AndroidColor.green(color) / 255f,
        blue = AndroidColor.blue(color) / 255f,
        alpha = AndroidColor.alpha(color) / 255f,
    ),
)

private class FixedGlanceColorProvider(
    private val color: ComposeColor,
) : ColorProvider {
    override fun getColor(context: Context): ComposeColor = color
}

private fun isDark(context: Context, appearance: WidgetAppearance): Boolean = when (appearance) {
    WidgetAppearance.DARK -> true
    WidgetAppearance.LIGHT -> false
    WidgetAppearance.SYSTEM -> context.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
}
