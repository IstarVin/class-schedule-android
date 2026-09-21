package com.istarvin.classschedule2.ui.widget

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istarvin.classschedule2.R
import com.istarvin.classschedule2.widget.WidgetAppearance
import com.istarvin.classschedule2.widget.WidgetColorSource
import com.istarvin.classschedule2.widget.WidgetLayout
import com.istarvin.classschedule2.widget.WidgetOptions
import com.istarvin.classschedule2.widget.WidgetTimeFormat

@Composable
internal fun WidgetConfigurationScreen(
    widgetLayout: WidgetLayout,
    initialOptions: WidgetOptions,
    isReconfigure: Boolean,
    onSave: (WidgetOptions) -> Unit,
) {
    var appearance by remember { mutableStateOf(initialOptions.appearance) }
    var colorSource by remember { mutableStateOf(initialOptions.colorSource) }
    var timeFormat by remember { mutableStateOf(initialOptions.timeFormat) }
    var showRoom by remember { mutableStateOf(initialOptions.showRoom) }
    var showNext by remember { mutableStateOf(initialOptions.showNext) }
    var showProgress by remember { mutableStateOf(initialOptions.showProgress) }

    val appearanceEntries = stringArrayResource(R.array.widget_appearance_entries).toList()
    val colorEntries = stringArrayResource(R.array.widget_color_entries).toList()
    val timeEntries = stringArrayResource(R.array.widget_time_entries).toList()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text(
                text = stringResource(R.string.widget_config_eyebrow),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = stringResource(R.string.widget_config_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = stringResource(R.string.widget_config_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )

            Spacer(modifier = Modifier.height(22.dp))
            WidgetPreview(
                widgetLayout = widgetLayout,
                appearance = appearance,
                colorSource = colorSource,
                showRoom = showRoom,
                showNext = showNext,
                showProgress = showProgress,
            )

            Spacer(modifier = Modifier.height(28.dp))
            ConfigurationSection(
                title = stringResource(R.string.widget_config_section_style),
                subtitle = stringResource(R.string.widget_config_section_style_subtitle),
            ) {
                ConfigurationDropdown(
                    label = stringResource(R.string.widget_config_appearance),
                    selectedIndex = appearance.ordinal,
                    entries = appearanceEntries,
                    onSelected = { appearance = WidgetAppearance.entries[it] },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ConfigurationDropdown(
                    label = stringResource(R.string.widget_config_colors),
                    selectedIndex = colorSource.ordinal,
                    entries = colorEntries,
                    onSelected = { colorSource = WidgetColorSource.entries[it] },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ConfigurationDropdown(
                    label = stringResource(R.string.widget_config_time),
                    selectedIndex = timeFormat.ordinal,
                    entries = timeEntries,
                    onSelected = { timeFormat = WidgetTimeFormat.entries[it] },
                )
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                onClick = {
                    onSave(
                        WidgetOptions(
                            appearance = appearance,
                            colorSource = colorSource,
                            timeFormat = timeFormat,
                            showRoom = showRoom,
                            showNext = showNext,
                            showProgress = showProgress,
                        ),
                    )
                },
            ) {
                Text(
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    text = stringResource(
                        if (isReconfigure) R.string.widget_config_save else R.string.widget_config_add,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConfigurationSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ConfigurationDropdown(
    label: String,
    selectedIndex: Int,
    entries: List<String>,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val fieldShape = RoundedCornerShape(14.dp)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 4.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
                    .clip(fieldShape)
                    .clickable { expanded = true },
                shape = fieldShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (expanded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entries[selectedIndex],
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = stringResource(R.string.widget_config_dropdown_chevron),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.88f),
            ) {
                entries.forEachIndexed { index, entry ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = entry,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            onSelected(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private data class PreviewColors(
    val card: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
)

@Composable
private fun WidgetPreview(
    widgetLayout: WidgetLayout,
    appearance: WidgetAppearance,
    colorSource: WidgetColorSource,
    showRoom: Boolean,
    showNext: Boolean,
    showProgress: Boolean,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance) {
        WidgetAppearance.DARK -> true
        WidgetAppearance.LIGHT -> false
        WidgetAppearance.SYSTEM -> systemDark
    }
    val useDynamicColor = colorSource == WidgetColorSource.MATERIAL_YOU &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = if (useDynamicColor) {
        PreviewColors(
            card = colorResource(
                if (dark) android.R.color.system_neutral1_800
                else android.R.color.system_neutral1_100,
            ),
            primary = colorResource(
                if (dark) android.R.color.system_neutral1_50
                else android.R.color.system_neutral1_900,
            ),
            secondary = colorResource(
                if (dark) android.R.color.system_neutral1_200
                else android.R.color.system_neutral1_700,
            ),
            accent = colorResource(
                if (dark) android.R.color.system_accent1_200
                else android.R.color.system_accent1_600,
            ),
        )
    } else {
        PreviewColors(
            card = colorResource(if (dark) R.color.widget_dark_panel else R.color.widget_light_panel),
            primary = colorResource(if (dark) R.color.widget_dark_text_primary else R.color.widget_light_text_primary),
            secondary = colorResource(if (dark) R.color.widget_dark_text_secondary else R.color.widget_light_text_secondary),
            accent = colorResource(if (dark) R.color.widget_dark_accent else R.color.widget_light_accent),
        )
    }

    val previewLabel = when (widgetLayout) {
        WidgetLayout.COMPACT -> stringResource(R.string.widget_config_preview_compact_label)
        WidgetLayout.MEDIUM -> stringResource(R.string.widget_config_preview_medium_label)
        WidgetLayout.LARGE -> stringResource(R.string.widget_config_preview_large_label)
        WidgetLayout.STACKED -> stringResource(R.string.widget_config_preview_stacked_label)
    }
    val previewCaption = when (widgetLayout) {
        WidgetLayout.COMPACT -> stringResource(R.string.widget_config_preview_compact_caption)
        WidgetLayout.MEDIUM -> stringResource(R.string.widget_config_preview_medium_caption)
        WidgetLayout.LARGE -> stringResource(R.string.widget_config_preview_large_caption)
        WidgetLayout.STACKED -> stringResource(R.string.widget_config_preview_stacked_caption)
    }
    val previewDetails = buildList {
        if (showRoom) add(stringResource(R.string.widget_config_preview_room))
        if (showNext) add(stringResource(R.string.widget_config_preview_next))
    }.ifEmpty { listOf(stringResource(R.string.widget_config_preview_empty)) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.2f)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.widget_config_preview),
                color = colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Text(
                modifier = Modifier.padding(top = 10.dp),
                text = previewLabel,
                color = colors.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.widget_config_preview_subject),
                color = colors.primary,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = previewCaption,
                color = colors.secondary,
                fontSize = 13.sp,
            )
            Text(
                modifier = Modifier.padding(top = 5.dp),
                text = previewDetails.joinToString(" · "),
                color = colors.secondary,
                fontSize = 13.sp,
            )
            if (showProgress) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.accent),
                )
            }
            if (widgetLayout == WidgetLayout.STACKED && showNext) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.18f)),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.widget_glance_up_next_title),
                            color = colors.secondary,
                            fontSize = 12.sp,
                        )
                        Text(
                            modifier = Modifier.padding(top = 4.dp),
                            text = stringResource(R.string.widget_config_preview_next_subject),
                            color = colors.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            modifier = Modifier.padding(top = 4.dp),
                            text = stringResource(R.string.widget_config_preview_next_meta),
                            color = colors.secondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}
