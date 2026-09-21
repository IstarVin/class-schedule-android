package com.istarvin.classschedule2.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as GlanceColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.istarvin.classschedule2.MainActivity
import com.istarvin.classschedule2.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared Glance implementation for all three widget sizes. */
internal abstract class ScheduleGlanceWidget(
    private val layout: WidgetLayout,
) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val appWidgetId = GlanceAppWidgetManager(appContext).getAppWidgetId(id)
        val snapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            WidgetUpdateCoordinator.snapshotFor(
                WidgetScheduleReader.load(appContext),
                ZonedDateTime.now(),
            )
        }
        val initialOptions = WidgetPreferences.read(appContext, appWidgetId)
        val snapshots = WidgetSnapshotState.observe(appWidgetId, snapshot)
        WidgetRefreshScheduler.schedule(appContext, snapshot.refreshAt)
        provideContent {
            // Glance may recompose an existing session instead of restarting provideGlance when
            // update() is called. Observe both sources so every widget value is refreshed in the
            // existing composition as well as when a new one is created.
            val options by WidgetPreferences.observe(appContext, appWidgetId)
                .collectAsState(initial = initialOptions)
            val currentSnapshot by snapshots.collectAsState()
            ScheduleGlanceContent(appContext, layout, options, currentSnapshot)
        }
    }
}

internal object ClassScheduleCompactGlanceWidget : ScheduleGlanceWidget(WidgetLayout.COMPACT)

internal object ClassScheduleMediumGlanceWidget : ScheduleGlanceWidget(WidgetLayout.MEDIUM)

internal object ClassScheduleLargeGlanceWidget : ScheduleGlanceWidget(WidgetLayout.LARGE)

internal object ClassScheduleStackedGlanceWidget : ScheduleGlanceWidget(WidgetLayout.STACKED)

@Composable
private fun ScheduleGlanceContent(
    context: Context,
    layout: WidgetLayout,
    options: WidgetOptions,
    snapshot: WidgetSnapshot,
) {
    when (layout) {
        WidgetLayout.COMPACT -> CompactGlanceContent(context, options, snapshot)
        WidgetLayout.MEDIUM -> MediumGlanceContent(context, options, snapshot)
        WidgetLayout.LARGE -> LargeGlanceContent(context, options, snapshot)
        WidgetLayout.STACKED -> StackedGlanceContent(context, options, snapshot)
    }
}

@Composable
private fun CompactGlanceContent(
    context: Context,
    options: WidgetOptions,
    snapshot: WidgetSnapshot,
) {
    val colors = glancePalette(context, options)
    val primary = snapshot.current ?: snapshot.next?.scheduleClass
    val isCurrent = snapshot.current != null
    val openApp = actionStartActivity<MainActivity>()
    val refresh = refreshAction(context)

    Column(
        modifier = rootModifier(colors, openApp).padding(14.dp),
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
            RefreshButton(context, colors, refresh)
        }

        if (primary == null) {
            EmptyPrimaryContent(context, colors, compact = true)
        } else {
            PrimaryContent(
                context = context,
                options = options,
                snapshot = snapshot,
                colors = colors,
                primary = primary,
                isCurrent = isCurrent,
                topPadding = 9,
                titleSize = 21,
                showProgress = false,
            )
            if (options.showNext) {
                val nextLine = if (isCurrent) {
                    snapshot.next?.let {
                        context.getString(
                            R.string.widget_glance_next_line,
                            it.scheduleClass.code,
                            dateTime(context, it, options, snapshot.now.toLocalDate()),
                        )
                    }
                } else {
                    snapshot.following?.let {
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
    }
}

@Composable
private fun StackedGlanceContent(
    context: Context,
    options: WidgetOptions,
    snapshot: WidgetSnapshot,
) {
    val colors = glancePalette(context, options)
    val openApp = actionStartActivity<MainActivity>()

    val current = snapshot.current?.let { ScheduledClass(it, snapshot.now.toLocalDate()) }
    val next = snapshot.next

    Column(
        modifier = rootModifier(colors, openApp).padding(12.dp),
    ) {
        if (current == null) {
            if (snapshot.today.isEmpty() && next == null) {
                StackedEmptyCard(
                    context = context,
                    colors = colors,
                    modifier = GlanceModifier.defaultWeight(),
                )
            } else {
                StackedBreakCard(
                    context = context,
                    colors = colors,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        } else {
            StackedClassCard(
                context = context,
                options = options,
                snapshot = snapshot,
                scheduled = current,
                isCurrent = true,
                label = R.string.widget_glance_right_now,
                accent = colors.nowAccent,
                colors = colors,
                modifier = GlanceModifier.defaultWeight(),
            )
        }

        if (options.showNext && next != null) {
            Spacer(GlanceModifier.height(10.dp))
            StackedClassCard(
                context = context,
                options = options,
                snapshot = snapshot,
                scheduled = next,
                isCurrent = false,
                label = R.string.widget_glance_up_next_title,
                accent = colors.nextAccent,
                colors = colors,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun StackedBreakCard(
    context: Context,
    colors: GlancePalette,
    modifier: GlanceModifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.divider)
            .cornerRadius(12.dp),
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = 3.dp),
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(colors.panel)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 17.dp, vertical = 14.dp),
            ) {
                Text(
                    text = context.getString(R.string.widget_glance_right_now),
                    style = TextStyle(color = colors.secondary, fontSize = 10.sp),
                    maxLines = 1,
                )
                Text(
                    text = context.getString(R.string.widget_glance_no_class_in_session),
                    modifier = GlanceModifier.padding(top = 14.dp),
                    style = TextStyle(color = colors.secondary, fontSize = 12.sp),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun StackedClassCard(
    context: Context,
    options: WidgetOptions,
    snapshot: WidgetSnapshot,
    scheduled: ScheduledClass,
    isCurrent: Boolean,
    label: Int,
    accent: ColorProvider,
    colors: GlancePalette,
    modifier: GlanceModifier,
) {
    val item = scheduled.scheduleClass

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(accent)
            .cornerRadius(12.dp),
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = 3.dp),
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(colors.panel)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 17.dp, vertical = 14.dp),
            ) {
                Text(
                    text = context.getString(label),
                    style = TextStyle(color = colors.secondary, fontSize = 10.sp),
                    maxLines = 1,
                )
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 9.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Text(
                        text = item.code,
                        style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    if (item.section.isNotBlank()) {
                        Text(
                            text = item.section,
                            modifier = GlanceModifier.padding(start = 8.dp),
                            style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text = item.name,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 3.dp),
                    style = TextStyle(color = colors.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                )
                Text(
                    text = stackedTimeDetails(context, item, options),
                    modifier = GlanceModifier.padding(top = 6.dp),
                    style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                    maxLines = 1,
                )
                if (isCurrent && options.showProgress) {
                    val elapsed = (snapshot.now.hour * 60 + snapshot.now.minute) - item.startMinute
                    val duration = (item.endMinute - item.startMinute).coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .height(4.dp),
                        color = accent,
                        backgroundColor = colors.divider,
                    )
                }
            }
        }
    }
}

@Composable
private fun StackedEmptyCard(
    context: Context,
    colors: GlancePalette,
    modifier: GlanceModifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.panel)
            .cornerRadius(12.dp)
            .padding(17.dp),
    ) {
        Column {
            Text(
                text = context.getString(R.string.widget_glance_no_schedule_code),
                style = TextStyle(color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = context.getString(R.string.widget_glance_no_schedule_subject),
                modifier = GlanceModifier.padding(top = 8.dp),
                style = TextStyle(color = colors.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = context.getString(R.string.widget_glance_no_schedule_time),
                modifier = GlanceModifier.padding(top = 5.dp),
                style = TextStyle(color = colors.secondary, fontSize = 12.sp),
            )
        }
    }
}

private fun stackedTimeDetails(context: Context, item: ScheduleClass, options: WidgetOptions): String {
    val range = timeRange(context, item, options)
    return if (options.showRoom && item.room.isNotBlank()) {
        context.getString(R.string.widget_glance_room_time, item.room, range)
    } else {
        range
    }
}

@Composable
private fun MediumGlanceContent(
    context: Context,
    options: WidgetOptions,
    snapshot: WidgetSnapshot,
) {
    val colors = glancePalette(context, options)
    val primary = snapshot.current ?: snapshot.next?.scheduleClass
    val secondary = if (snapshot.current != null) snapshot.next else snapshot.following
    val isCurrent = snapshot.current != null
    val openApp = actionStartActivity<MainActivity>()
    val refresh = refreshAction(context)

    Row(
        modifier = rootModifier(colors, openApp).padding(12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .background(colors.panel)
                .cornerRadius(12.dp)
                .padding(13.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    text = glanceStateLabel(context, snapshot),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = colors.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                RefreshButton(context, colors, refresh)
            }
            if (primary == null) {
                EmptyPrimaryContent(context, colors, compact = false)
            } else {
                PrimaryContent(
                    context = context,
                    options = options,
                    snapshot = snapshot,
                    colors = colors,
                    primary = primary,
                    isCurrent = isCurrent,
                    topPadding = 3,
                    titleSize = 18,
                    showProgress = true,
                )
            }
        }

        if (options.showNext && secondary != null) {
            Spacer(
                GlanceModifier
                    .width(9.dp)
                    .fillMaxHeight(),
            )
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .background(colors.panel)
                    .cornerRadius(12.dp)
                    .padding(13.dp),
            ) {
                Text(
                    text = context.getString(
                        if (isCurrent) R.string.widget_glance_next_label else R.string.widget_glance_then_label,
                    ),
                    style = TextStyle(color = colors.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    text = secondary.scheduleClass.code,
                    modifier = GlanceModifier.padding(top = 3.dp),
                    style = TextStyle(color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                if (secondary.scheduleClass.section.isNotBlank()) {
                    Text(
                        text = secondary.scheduleClass.section,
                        style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                        maxLines = 1,
                    )
                }
                Text(
                    text = secondary.scheduleClass.name,
                    modifier = GlanceModifier.padding(top = 2.dp),
                    style = TextStyle(color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 3,
                )
                Text(
                    text = dateTime(context, secondary, options, snapshot.now.toLocalDate()),
                    modifier = GlanceModifier.padding(top = 6.dp),
                    style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                    maxLines = 1,
                )
                roomText(context, secondary.scheduleClass.room, options)?.let {
                    Text(
                        text = it,
                        style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun LargeGlanceContent(
    context: Context,
    options: WidgetOptions,
    snapshot: WidgetSnapshot,
) {
    val colors = glancePalette(context, options)
    val openApp = actionStartActivity<MainActivity>()
    val refresh = refreshAction(context)
    val locale = context.resources.configuration.locales[0]

    Column(
        modifier = rootModifier(colors, openApp).padding(14.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = context.getString(R.string.widget_glance_no_schedule_code),
                    style = TextStyle(color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    text = snapshot.now.format(DateTimeFormatter.ofPattern("EEEE · MMM d", locale)).uppercase(locale),
                    modifier = GlanceModifier.padding(top = 4.dp),
                    style = TextStyle(color = colors.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
            RefreshButton(context, colors, refresh)
        }
        Text(
            text = todaySummary(context, snapshot),
            modifier = GlanceModifier.padding(top = 5.dp, bottom = 6.dp),
            style = TextStyle(color = colors.secondary, fontSize = 12.sp),
            maxLines = 1,
        )

        if (snapshot.today.isEmpty()) {
            Text(
                text = snapshot.next?.let {
                    context.getString(
                        R.string.widget_glance_empty_next,
                        it.scheduleClass.name,
                        dateTime(context, it, options, snapshot.now.toLocalDate()),
                    )
                } ?: context.getString(R.string.widget_glance_empty_schedule),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                style = TextStyle(color = colors.secondary, fontSize = 14.sp, textAlign = TextAlign.Center),
                maxLines = 2,
            )
        } else {
            snapshot.today.take(5).forEachIndexed { index, item ->
                LargeClassRow(context, options, snapshot, colors, item, index)
            }
            if (snapshot.today.size > 5) {
                Text(
                    text = context.resources.getQuantityString(
                        R.plurals.widget_glance_more_classes,
                        snapshot.today.size - 5,
                        snapshot.today.size - 5,
                    ),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 3.dp),
                    style = TextStyle(color = colors.secondary, fontSize = 11.sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
            }
        }

        snapshot.next?.let { next ->
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(colors.panel)
                    .cornerRadius(10.dp)
                    .padding(9.dp),
            ) {
                Text(
                    text = context.getString(R.string.widget_glance_up_next_label),
                    style = TextStyle(color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    text = next.scheduleClass.name,
                    modifier = GlanceModifier.padding(top = 2.dp),
                    style = TextStyle(color = colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                )
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 3.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Text(
                        text = if (next.date == snapshot.now.toLocalDate()) {
                            time(context, next.scheduleClass.startMinute, options)
                        } else {
                            dateTime(context, next, options, snapshot.now.toLocalDate())
                        },
                        style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                        maxLines = 1,
                    )
                    roomText(context, next.scheduleClass.room, options)?.let {
                        Text(
                            text = it,
                            modifier = GlanceModifier.padding(start = 8.dp),
                            style = TextStyle(color = colors.secondary, fontSize = 11.sp),
                            maxLines = 1,
                        )
                    }
                }
                if (options.showNext) {
                    snapshot.following?.let {
                        Text(
                            text = context.getString(
                                R.string.widget_glance_then_line,
                                it.scheduleClass.code,
                                dateTime(context, it, options, snapshot.now.toLocalDate()),
                            ),
                            modifier = GlanceModifier.padding(top = 3.dp),
                            style = TextStyle(color = colors.secondary, fontSize = 10.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LargeClassRow(
    context: Context,
    options: WidgetOptions,
    snapshot: WidgetSnapshot,
    colors: GlancePalette,
    item: ScheduleClass,
    index: Int,
) {
    val minute = snapshot.now.hour * 60 + snapshot.now.minute
    val isCurrent = snapshot.current == item
    val isPast = minute >= item.endMinute
    val isFirstUpcoming = snapshot.next?.let {
        it.date == snapshot.now.toLocalDate() && it.scheduleClass == item
    } == true
    val statusColor = when {
        isCurrent || isFirstUpcoming -> colors.accent
        isPast -> colors.muted
        else -> colors.secondary
    }
    val textColor = if (isPast) colors.muted else colors.primary

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = if (index == 0) 3.dp else 3.dp)
            .background(if (isCurrent) colors.current else colors.panel)
            .cornerRadius(8.dp)
            .padding(7.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(38.dp)
                .background(statusColor),
        ) {}
        Column(
            modifier = GlanceModifier
                .width(58.dp)
                .padding(start = 8.dp),
        ) {
            Text(
                text = time(context, item.startMinute, options),
                style = TextStyle(color = if (isPast) colors.muted else colors.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = time(context, item.endMinute, options),
                style = TextStyle(color = colors.muted, fontSize = 9.sp),
                maxLines = 1,
            )
        }
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(start = 8.dp),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = item.code,
                    style = TextStyle(color = if (isPast) colors.muted else colors.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                if (item.section.isNotBlank()) {
                    Text(
                        text = item.section,
                        modifier = GlanceModifier.padding(start = 7.dp),
                        style = TextStyle(color = if (isPast) colors.muted else colors.secondary, fontSize = 9.sp),
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = item.name,
                style = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            roomText(context, item.room, options)?.let {
                Text(
                    text = it,
                    style = TextStyle(color = if (isPast) colors.muted else colors.secondary, fontSize = 10.sp),
                    maxLines = 1,
                )
            }
        }
        if (isCurrent) {
            Text(
                text = context.getString(R.string.widget_glance_now_label),
                modifier = GlanceModifier.padding(start = 6.dp),
                style = TextStyle(color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PrimaryContent(
    context: Context,
    options: WidgetOptions,
    snapshot: WidgetSnapshot,
    colors: GlancePalette,
    primary: ScheduleClass,
    isCurrent: Boolean,
    topPadding: Int,
    titleSize: Int,
    showProgress: Boolean,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = topPadding.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = primary.code,
            style = TextStyle(color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
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
            .padding(top = 2.dp),
        style = TextStyle(color = colors.primary, fontSize = titleSize.sp, fontWeight = FontWeight.Bold),
        maxLines = if (titleSize > 18) 2 else 3,
    )
    Text(
        text = if (isCurrent) {
            timeRange(context, primary, options)
        } else {
            dateTime(context, snapshot.next!!, options, snapshot.now.toLocalDate())
        },
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 5.dp),
        style = TextStyle(color = colors.secondary, fontSize = 11.sp),
        maxLines = 1,
    )
    roomText(context, primary.room, options)?.let {
        Text(
            text = it,
            style = TextStyle(color = colors.secondary, fontSize = 11.sp),
            maxLines = 1,
        )
    }
    if (showProgress && isCurrent && options.showProgress) {
        val elapsed = (snapshot.now.hour * 60 + snapshot.now.minute) - primary.startMinute
        val duration = (primary.endMinute - primary.startMinute).coerceAtLeast(1)
        LinearProgressIndicator(
            progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f),
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .height(4.dp),
            color = colors.accent,
            backgroundColor = colors.divider,
        )
    }
}

@Composable
private fun EmptyPrimaryContent(context: Context, colors: GlancePalette, compact: Boolean) {
    Text(
        text = context.getString(R.string.widget_glance_no_schedule_code),
        modifier = GlanceModifier.padding(top = if (compact) 9.dp else 3.dp),
        style = TextStyle(color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
    Text(
        text = context.getString(R.string.widget_glance_no_schedule_subject),
        modifier = GlanceModifier.padding(top = 4.dp),
        style = TextStyle(color = colors.primary, fontSize = if (compact) 21.sp else 18.sp, fontWeight = FontWeight.Bold),
        maxLines = 2,
    )
    Text(
        text = context.getString(
            if (compact) R.string.widget_glance_no_schedule_time else R.string.widget_glance_empty_schedule,
        ),
        modifier = GlanceModifier.padding(top = 7.dp),
        style = TextStyle(color = colors.secondary, fontSize = 12.sp),
        maxLines = 2,
    )
}

@Composable
private fun RefreshButton(
    context: Context,
    colors: GlancePalette,
    refresh: androidx.glance.action.Action,
) {
    Text(
        text = context.getString(R.string.widget_glance_refresh_symbol),
        modifier = GlanceModifier
            .size(28.dp)
            .background(colors.panel)
            .cornerRadius(14.dp)
            .clickable(refresh),
        style = TextStyle(color = colors.accent, fontSize = 17.sp, textAlign = TextAlign.Center),
        maxLines = 1,
    )
}

private fun rootModifier(
    colors: GlancePalette,
    openApp: androidx.glance.action.Action,
): GlanceModifier = GlanceModifier
    .fillMaxSize()
    .background(colors.surface)
    .cornerRadius(24.dp)
    .clickable(openApp)

private fun refreshAction(context: Context): androidx.glance.action.Action = actionSendBroadcast(
    Intent(context, WidgetRefreshReceiver::class.java)
        .setAction(WidgetRefreshReceiver.ACTION_MANUAL_REFRESH),
)

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

private fun todaySummary(context: Context, snapshot: WidgetSnapshot): String =
    if (snapshot.today.isEmpty()) {
        context.getString(R.string.widget_glance_state_no_classes)
    } else {
        context.resources.getQuantityString(
            R.plurals.widget_glance_class_count,
            snapshot.today.size,
            snapshot.today.size,
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

private fun roomText(context: Context, room: String, options: WidgetOptions): String? =
    room.takeIf { options.showRoom && it.isNotBlank() }
        ?.let { context.getString(R.string.widget_glance_room, it) }

private data class GlancePalette(
    val surface: ColorProvider,
    val panel: ColorProvider,
    val current: ColorProvider,
    val divider: ColorProvider,
    val primary: ColorProvider,
    val secondary: ColorProvider,
    val muted: ColorProvider,
    val accent: ColorProvider,
    val nowAccent: ColorProvider,
    val nextAccent: ColorProvider,
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
    val resources = if (dynamic) {
        intArrayOf(
            if (dark) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900,
            if (dark) android.R.color.system_neutral1_200 else android.R.color.system_neutral1_700,
            if (dark) android.R.color.system_neutral1_400 else android.R.color.system_neutral1_500,
            if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600,
        )
    } else {
        when (options.appearance) {
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
    }
    val nowAccent = if (dynamic) {
        context.getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
    } else {
        context.getColor(
            when (options.appearance) {
                WidgetAppearance.SYSTEM -> R.color.widget_now_accent
                WidgetAppearance.LIGHT -> R.color.widget_light_now_accent
                WidgetAppearance.DARK -> R.color.widget_dark_now_accent
            },
        )
    }
    val nextAccent = if (dynamic) {
        context.getColor(if (dark) android.R.color.system_accent2_200 else android.R.color.system_accent2_600)
    } else {
        context.getColor(
            when (options.appearance) {
                WidgetAppearance.SYSTEM -> R.color.widget_next_accent
                WidgetAppearance.LIGHT -> R.color.widget_light_next_accent
                WidgetAppearance.DARK -> R.color.widget_dark_next_accent
            },
        )
    }
    val current = if (dynamic) {
        context.getColor(if (dark) android.R.color.system_neutral1_700 else android.R.color.system_neutral1_100)
    } else {
        context.getColor(
            when (options.appearance) {
                WidgetAppearance.SYSTEM -> R.color.widget_current
                WidgetAppearance.LIGHT -> R.color.widget_light_current
                WidgetAppearance.DARK -> R.color.widget_dark_current
            },
        )
    }
    val divider = if (dynamic) {
        context.getColor(if (dark) android.R.color.system_neutral1_700 else android.R.color.system_neutral1_200)
    } else {
        context.getColor(
            when (options.appearance) {
                WidgetAppearance.SYSTEM -> R.color.widget_divider
                WidgetAppearance.LIGHT -> R.color.widget_divider
                WidgetAppearance.DARK -> R.color.widget_divider
            },
        )
    }
    return GlancePalette(
        surface = glanceColor(surface),
        panel = glanceColor(panel),
        current = glanceColor(current),
        divider = glanceColor(divider),
        primary = glanceColor(context.getColor(resources[0])),
        secondary = glanceColor(context.getColor(resources[1])),
        muted = glanceColor(context.getColor(resources[2])),
        accent = glanceColor(context.getColor(resources[3])),
        nowAccent = glanceColor(nowAccent),
        nextAccent = glanceColor(nextAccent),
    )
}

private fun glanceColor(color: Int): ColorProvider {
    val composeColor = ComposeColor(
        red = AndroidColor.red(color) / 255f,
        green = AndroidColor.green(color) / 255f,
        blue = AndroidColor.blue(color) / 255f,
        alpha = AndroidColor.alpha(color) / 255f,
    )
    // The two-argument public factory produces a provider Glance's RemoteViews translator
    // understands for both backgrounds and text. Use the same value for both modes because the
    // palette has already resolved the widget's configured appearance.
    return GlanceColorProvider(composeColor, composeColor)
}

private fun isDark(context: Context, appearance: WidgetAppearance): Boolean = when (appearance) {
    WidgetAppearance.DARK -> true
    WidgetAppearance.LIGHT -> false
    WidgetAppearance.SYSTEM -> context.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
}
