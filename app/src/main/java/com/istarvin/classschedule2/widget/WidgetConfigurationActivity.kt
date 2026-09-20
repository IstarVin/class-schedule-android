package com.istarvin.classschedule2.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.res.Configuration
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.istarvin.classschedule2.R

/** Small native setup flow launched by the widget host before it binds an instance. */
class WidgetConfigurationActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val expectedProvider = ComponentName(this, AjScheduleWidgetProvider::class.java)
        if (AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider != expectedProvider) {
            // This exported activity is intended only for IDs allocated to this provider.
            finish()
            return
        }

        setContentView(R.layout.activity_widget_configuration)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.widget_config_title)

        val style = findViewById<Spinner>(R.id.config_style)
        val appearance = findViewById<Spinner>(R.id.config_appearance)
        val colors = findViewById<Spinner>(R.id.config_colors)
        val timeFormat = findViewById<Spinner>(R.id.config_time_format)
        val countdown = findViewById<Switch>(R.id.config_countdown)
        val room = findViewById<Switch>(R.id.config_room)
        val next = findViewById<Switch>(R.id.config_next)
        val progress = findViewById<Switch>(R.id.config_progress)
        val previewCard = findViewById<LinearLayout>(R.id.config_preview_card)
        val previewLabel = findViewById<TextView>(R.id.config_preview_label)
        val previewSubject = findViewById<TextView>(R.id.config_preview_subject)
        val preview = findViewById<TextView>(R.id.config_preview_caption)
        val previewDetails = findViewById<TextView>(R.id.config_preview_details)
        val previewProgress = findViewById<View>(R.id.config_preview_progress)

        WidgetPreferences.read(this, appWidgetId).also { saved ->
            style.setSelection(saved.style.ordinal)
            appearance.setSelection(saved.appearance.ordinal)
            colors.setSelection(saved.colorSource.ordinal)
            timeFormat.setSelection(saved.timeFormat.ordinal)
            countdown.isChecked = saved.showCountdown
            room.isChecked = saved.showRoom
            next.isChecked = saved.showNext
            progress.isChecked = saved.showProgress
        }
        fun updatePreview() {
            val selectedStyle = WidgetStyle.entries[style.selectedItemPosition]
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

            previewLabel.text = when (selectedStyle) {
                WidgetStyle.COMPACT -> "UP NEXT · ITE183"
                WidgetStyle.LARGE -> "TODAY · ITE183"
                else -> "NOW · ITE183"
            }
            preview.text = when (selectedStyle) {
                WidgetStyle.COMPACT -> "Compact · next class at a glance"
                WidgetStyle.MEDIUM -> "Now + next · the balanced default"
                WidgetStyle.LARGE -> "Today · your whole school day"
                WidgetStyle.AUTOMATIC -> "Responsive · adapts as you resize"
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
        style.onItemSelectedListener = selectionListener
        appearance.onItemSelectedListener = selectionListener
        colors.onItemSelectedListener = selectionListener
        timeFormat.onItemSelectedListener = selectionListener
        val switchListener = CompoundButton.OnCheckedChangeListener { _, _ -> updatePreview() }
        countdown.setOnCheckedChangeListener(switchListener)
        room.setOnCheckedChangeListener(switchListener)
        next.setOnCheckedChangeListener(switchListener)
        progress.setOnCheckedChangeListener(switchListener)
        updatePreview()

        findViewById<Button>(R.id.config_add).setOnClickListener {
            WidgetPreferences.save(
                this,
                appWidgetId,
                WidgetOptions(
                    style = WidgetStyle.entries[style.selectedItemPosition],
                    appearance = WidgetAppearance.entries[appearance.selectedItemPosition],
                    colorSource = WidgetColorSource.entries[colors.selectedItemPosition],
                    timeFormat = WidgetTimeFormat.entries[timeFormat.selectedItemPosition],
                    showCountdown = countdown.isChecked,
                    showRoom = room.isChecked,
                    showNext = next.isChecked,
                    showProgress = progress.isChecked,
                ),
            )
            WidgetUpdateCoordinator.update(this, intArrayOf(appWidgetId))
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
