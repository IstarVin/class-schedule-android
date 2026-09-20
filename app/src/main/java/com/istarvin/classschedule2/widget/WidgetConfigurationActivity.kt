package com.istarvin.classschedule2.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.istarvin.classschedule2.R

/** Small native setup flow launched by the widget host before it binds an instance. */
class WidgetConfigurationActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_RECONFIGURE = "com.istarvin.classschedule2.extra.RECONFIGURE_WIDGET"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        val widgetLayout = WidgetProviderRegistry.layoutFor(provider)
        if (widgetLayout == null) {
            // This exported activity is intended only for IDs allocated to this provider.
            finish()
            return
        }

        setContentView(R.layout.activity_widget_configuration)
        applySystemBarInsets(findViewById(R.id.config_root))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.widget_config_title)

        val appearance = findViewById<Spinner>(R.id.config_appearance)
        val colors = findViewById<Spinner>(R.id.config_colors)
        val timeFormat = findViewById<Spinner>(R.id.config_time_format)
        val countdown = findViewById<SwitchCompat>(R.id.config_countdown)
        val room = findViewById<SwitchCompat>(R.id.config_room)
        val next = findViewById<SwitchCompat>(R.id.config_next)
        val progress = findViewById<SwitchCompat>(R.id.config_progress)
        val previewCard = findViewById<LinearLayout>(R.id.config_preview_card)
        val previewLabel = findViewById<TextView>(R.id.config_preview_label)
        val previewSubject = findViewById<TextView>(R.id.config_preview_subject)
        val preview = findViewById<TextView>(R.id.config_preview_caption)
        val previewDetails = findViewById<TextView>(R.id.config_preview_details)
        val previewProgress = findViewById<View>(R.id.config_preview_progress)

        WidgetPreferences.read(this, appWidgetId).also { saved ->
            appearance.setSelection(saved.appearance.ordinal)
            colors.setSelection(saved.colorSource.ordinal)
            timeFormat.setSelection(saved.timeFormat.ordinal)
            countdown.isChecked = saved.showCountdown
            room.isChecked = saved.showRoom
            next.isChecked = saved.showNext
            progress.isChecked = saved.showProgress
        }
        fun updatePreview() {
            val selectedAppearance = WidgetAppearance.entries[appearance.selectedItemPosition]
            val useDynamicColor = colors.selectedItemPosition == WidgetColorSource.MATERIAL_YOU.ordinal &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val dark = when (selectedAppearance) {
                WidgetAppearance.DARK -> true
                WidgetAppearance.LIGHT -> false
                WidgetAppearance.SYSTEM -> resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }
            previewCard.setBackgroundResource(
                when {
                    useDynamicColor && dark -> R.drawable.widget_panel_background_dynamic_dark
                    useDynamicColor -> R.drawable.widget_panel_background_dynamic_light
                    dark -> R.drawable.widget_panel_background_dark
                    else -> R.drawable.widget_panel_background_light
                },
            )
            val (primary, secondary, accent) = if (useDynamicColor) {
                Triple(
                    getColor(if (dark) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900),
                    getColor(if (dark) android.R.color.system_neutral1_200 else android.R.color.system_neutral1_700),
                    getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600),
                )
            } else {
                Triple(
                    getColor(if (dark) R.color.widget_dark_text_primary else R.color.widget_light_text_primary),
                    getColor(if (dark) R.color.widget_dark_text_secondary else R.color.widget_light_text_secondary),
                    getColor(if (dark) R.color.widget_dark_accent else R.color.widget_light_accent),
                )
            }
            previewLabel.setTextColor(accent)
            previewSubject.setTextColor(primary)
            preview.setTextColor(secondary)
            previewDetails.setTextColor(secondary)
            previewProgress.setBackgroundColor(accent)

            previewLabel.text = when (widgetLayout) {
                WidgetLayout.COMPACT -> "UP NEXT · ITE183"
                WidgetLayout.LARGE -> "TODAY · ITE183"
                WidgetLayout.MEDIUM -> "NOW · ITE183"
            }
            preview.text = when (widgetLayout) {
                WidgetLayout.COMPACT -> "Compact · next class at a glance"
                WidgetLayout.MEDIUM -> "Now + next · the balanced default"
                WidgetLayout.LARGE -> "Today · your whole school day"
            }
            previewDetails.text = buildList {
                if (room.isChecked) add("Room TBA")
                if (countdown.isChecked) add("45 min left")
                if (next.isChecked) add("Next · ITE105")
            }.ifEmpty { listOf("A clean glance at your schedule") }.joinToString(" · ")
            previewProgress.visibility = if (progress.isChecked) View.VISIBLE else View.GONE
        }

        val selectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updatePreview()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        appearance.onItemSelectedListener = selectionListener
        colors.onItemSelectedListener = selectionListener
        timeFormat.onItemSelectedListener = selectionListener
        val switchListener = CompoundButton.OnCheckedChangeListener { _, _ -> updatePreview() }
        countdown.setOnCheckedChangeListener(switchListener)
        room.setOnCheckedChangeListener(switchListener)
        next.setOnCheckedChangeListener(switchListener)
        progress.setOnCheckedChangeListener(switchListener)
        updatePreview()

        findViewById<Button>(R.id.config_add).apply {
            if (intent.getBooleanExtra(EXTRA_RECONFIGURE, false)) {
                setText(R.string.widget_config_save)
            }
        }.setOnClickListener {
            WidgetPreferences.save(
                this,
                appWidgetId,
                WidgetOptions(
                    appearance = WidgetAppearance.entries[appearance.selectedItemPosition],
                    colorSource = WidgetColorSource.entries[colors.selectedItemPosition],
                    timeFormat = WidgetTimeFormat.entries[timeFormat.selectedItemPosition],
                    showCountdown = countdown.isChecked,
                    showRoom = room.isChecked,
                    showNext = next.isChecked,
                    showProgress = progress.isChecked,
                ),
            )
            WidgetUpdateCoordinator.update(this, intArrayOf(appWidgetId), widgetLayout)
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun applySystemBarInsets(root: View) {
        val initialPadding = intArrayOf(
            root.paddingLeft,
            root.paddingTop,
            root.paddingRight,
            root.paddingBottom,
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(
                initialPadding[0],
                initialPadding[1] + systemBars.top,
                initialPadding[2],
                initialPadding[3] + systemBars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
