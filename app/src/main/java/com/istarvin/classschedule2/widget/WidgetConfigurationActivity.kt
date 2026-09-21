package com.istarvin.classschedule2.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.istarvin.classschedule2.R

/** Small Compose setup flow launched by the widget host before it binds an instance. */
class WidgetConfigurationActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_RECONFIGURE = "com.istarvin.classschedule2.extra.RECONFIGURE_WIDGET"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val widgetLayout = WidgetProviderRegistry.layoutFor(
            AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider,
        )
        if (widgetLayout == null) {
            // This exported activity is intended only for IDs allocated to this provider.
            finish()
            return
        }

        val savedOptions = WidgetPreferences.read(this, appWidgetId)
        val isReconfigure = intent.getBooleanExtra(EXTRA_RECONFIGURE, false)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            WidgetConfigurationTheme {
                WidgetConfigurationScreen(
                    widgetLayout = widgetLayout,
                    initialOptions = savedOptions,
                    isReconfigure = isReconfigure,
                    onSave = { options ->
                        WidgetPreferences.save(this, appWidgetId, options)
                        WidgetUpdateCoordinator.update(this, intArrayOf(appWidgetId), widgetLayout)
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        )
                        finish()
                    },
                )
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

@Composable
private fun WidgetConfigurationTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val lightBackground = Color(0xFFF6F8FC)
    val darkBackground = Color(0xFF12151B)
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(
                primary = Color(0xFFA9C7F5),
                onPrimary = Color(0xFF17304E),
                primaryContainer = Color(0xFF294A70),
                onPrimaryContainer = Color(0xFFD8E8FF),
                secondary = Color(0xFFBBC7D8),
                onSecondary = Color(0xFF27313E),
                secondaryContainer = Color(0xFF3D4858),
                onSecondaryContainer = Color(0xFFDCE5F3),
                background = darkBackground,
                onBackground = Color(0xFFEEF2F7),
                surface = darkBackground,
                onSurface = Color(0xFFEEF2F7),
                surfaceVariant = Color(0xFF252A33),
                onSurfaceVariant = Color(0xFFC0C8D3),
                outline = Color(0xFF8C96A5),
                outlineVariant = Color(0xFF3D4551),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF365D8D),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD7E6FC),
                onPrimaryContainer = Color(0xFF0D1B2A),
                secondary = Color(0xFF536477),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE0E8F2),
                onSecondaryContainer = Color(0xFF111A24),
                background = lightBackground,
                onBackground = Color(0xFF19212D),
                surface = lightBackground,
                onSurface = Color(0xFF19212D),
                surfaceVariant = Color(0xFFE8EDF5),
                onSurfaceVariant = Color(0xFF46515F),
                outline = Color(0xFF778494),
                outlineVariant = Color(0xFFC7D0DC),
            )
        },
        content = content,
    )
}

@Composable
private fun WidgetConfigurationScreen(
    widgetLayout: WidgetLayout,
    initialOptions: WidgetOptions,
    isReconfigure: Boolean,
    onSave: (WidgetOptions) -> Unit,
) {
    var appearance by remember { mutableStateOf(initialOptions.appearance) }
    var colorSource by remember { mutableStateOf(initialOptions.colorSource) }
    var timeFormat by remember { mutableStateOf(initialOptions.timeFormat) }
    var showCountdown by remember { mutableStateOf(initialOptions.showCountdown) }
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
            showCountdown = showCountdown,
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

        Spacer(modifier = Modifier.height(20.dp))
        ConfigurationSection(
            title = stringResource(R.string.widget_config_section_content),
            subtitle = stringResource(R.string.widget_config_section_content_subtitle),
        ) {
            ConfigurationSwitch(
                label = stringResource(R.string.widget_config_show_countdown),
                checked = showCountdown,
                onCheckedChange = { showCountdown = it },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ConfigurationSwitch(
                label = stringResource(R.string.widget_config_show_room),
                checked = showRoom,
                onCheckedChange = { showRoom = it },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ConfigurationSwitch(
                label = stringResource(R.string.widget_config_show_next),
                checked = showNext,
                onCheckedChange = { showNext = it },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ConfigurationSwitch(
                label = stringResource(R.string.widget_config_show_progress),
                checked = showProgress,
                onCheckedChange = { showProgress = it },
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
                        showCountdown = showCountdown,
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

@Composable
private fun ConfigurationSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
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
    showCountdown: Boolean,
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
    }
    val previewCaption = when (widgetLayout) {
        WidgetLayout.COMPACT -> stringResource(R.string.widget_config_preview_compact_caption)
        WidgetLayout.MEDIUM -> stringResource(R.string.widget_config_preview_medium_caption)
        WidgetLayout.LARGE -> stringResource(R.string.widget_config_preview_large_caption)
    }
    val previewDetails = buildList {
        if (showRoom) add(stringResource(R.string.widget_config_preview_room))
        if (showCountdown) add(stringResource(R.string.widget_config_preview_countdown))
        if (showNext) add(stringResource(R.string.widget_config_preview_next))
    }.ifEmpty { listOf(stringResource(R.string.widget_config_preview_empty)) }

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
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
        }
    }
}
