package com.istarvin.classschedule2.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.istarvin.classschedule2.ui.theme.ClassScheduleTheme
import com.istarvin.classschedule2.ui.widget.WidgetConfigurationScreen

/** Android entry point for the Compose setup flow launched by the widget host. */
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
        // The app supplies EXTRA_RECONFIGURE from its widget settings menu. Widget hosts
        // can also launch this activity directly when editing an existing widget, so use
        // the saved per-widget options as a fallback for that path.
        val isReconfigure = intent.getBooleanExtra(EXTRA_RECONFIGURE, false) ||
            WidgetPreferences.hasSavedOptions(this, appWidgetId)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ClassScheduleTheme {
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
