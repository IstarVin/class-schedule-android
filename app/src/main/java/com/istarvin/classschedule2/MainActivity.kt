package com.istarvin.classschedule2

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.istarvin.classschedule2.data.OfflineScheduleHtml
import com.istarvin.classschedule2.data.ScheduleCache
import com.istarvin.classschedule2.data.SchedulePreferences
import com.istarvin.classschedule2.data.ScheduleProfile
import com.istarvin.classschedule2.data.ScheduleSyncWorker
import com.istarvin.classschedule2.data.WebsiteCache
import com.istarvin.classschedule2.data.WebsiteSnapshot
import com.istarvin.classschedule2.widget.WidgetConfigurationActivity
import com.istarvin.classschedule2.widget.WidgetProviderRegistry
import com.istarvin.classschedule2.widget.WidgetUpdateCoordinator
import java.io.ByteArrayInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** A deliberately small native shell around the schedule's trusted web experience. */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val LIGHT_SURFACE = "#F3F1EA"
        private const val DARK_SURFACE = "#15181E"
        private const val MENU_REFRESH = 1
        private const val MENU_ADD_WIDGET = 2
        private const val MENU_WIDGET_SETTINGS = 3
        private const val MENU_ABOUT = 4
        private const val MENU_SCHEDULE_SETTINGS = 5
    }

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var webView: WebView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var offlineCard: LinearLayout
    private lateinit var offlineMessage: TextView
    private lateinit var retryButton: TextView
    private lateinit var dismissOfflineButton: TextView
    private var showingLocalScheduleFallback = false
    private lateinit var selectedSchedule: ScheduleProfile
    private var loadGeneration = 0
    private data class LoadedWebsite(val profile: ScheduleProfile, val snapshot: WebsiteSnapshot)
    @Volatile private var loadedWebsite: LoadedWebsite? = null
    private val websiteUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra(ScheduleSyncWorker.KEY_PROFILE) == selectedSchedule.id) {
                loadScheduleSite()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedSchedule = SchedulePreferences.selected(applicationContext)
        supportActionBar?.hide()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applySystemBarAppearance(isSystemDark())
        val contentView = createContentView()
        setContentView(contentView)
        applySystemBarInsets(contentView)
        configureWebView()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            },
        )

        ScheduleSyncWorker.ensurePeriodic(applicationContext)
        // Rehydrate the native snapshot before navigating, including after process recreation.
        loadScheduleSite()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, websiteUpdatedReceiver, IntentFilter(ScheduleSyncWorker.ACTION_WEBSITE_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        unregisterReceiver(websiteUpdatedReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        applySystemBarAppearance(isSystemDark())
        val profile = SchedulePreferences.selected(applicationContext)
        if (profile != selectedSchedule) applySchedule(profile) else refreshScheduleData()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.apply {
            stopLoading()
            loadUrl("about:blank")
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        ioExecutor.shutdown()
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            userAgentString = "$userAgentString Class-Schedule-Android/1.0"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        webView.setBackgroundColor((if (isSystemDark()) DARK_SURFACE else LIGHT_SURFACE).toColorInt())
        webView.webViewClient = ScheduleWebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                showLoading(newProgress < 100)
            }
        }
    }

    private fun createContentView(): View {
        return FrameLayout(this).apply {
            setBackgroundColor((if (isSystemDark()) DARK_SURFACE else LIGHT_SURFACE).toColorInt())

            webView = WebView(this@MainActivity).apply {
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )

            loadingIndicator = ProgressBar(this@MainActivity).apply {
                isIndeterminate = true
                contentDescription = getString(R.string.loading_schedule)
            }
            addView(
                loadingIndicator,
                FrameLayout.LayoutParams(44.dp, 44.dp, Gravity.CENTER),
            )

            offlineCard = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp, 14.dp, 16.dp, 14.dp)
                background = roundedBackground("#E91E2935")
                elevation = 8.dp.toFloat()
                visibility = View.GONE
            }
            offlineMessage = TextView(this@MainActivity).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
            }
            dismissOfflineButton = TextView(this@MainActivity).apply {
                text = getString(R.string.dismiss_offline_message_symbol)
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                contentDescription = getString(R.string.dismiss_offline_message)
                minWidth = 48.dp
                minHeight = 48.dp
                setOnClickListener { hideOfflineNotice() }
            }
            retryButton = TextView(this@MainActivity).apply {
                setTextColor("#A7F3D0".toColorInt())
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12.dp, 0, 0)
                text = getString(R.string.try_again)
                contentDescription = getString(R.string.retry_loading_schedule)
                setOnClickListener {
                    loadScheduleSite()
                    refreshScheduleData()
                }
            }
            offlineCard.addView(
                LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        offlineMessage,
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        ),
                    )
                    addView(
                        dismissOfflineButton,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            offlineCard.addView(
                retryButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                offlineCard,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM,
                ).apply {
                    setMargins(16.dp, 0, 16.dp, 20.dp)
                },
            )

            // This stays deliberately small: the schedule itself remains the app's UI.
            val overflowButton = TextView(this@MainActivity).apply {
                text = getString(R.string.more_options_symbol)
                textSize = 22f
                gravity = Gravity.CENTER
                contentDescription = getString(R.string.schedule_options)
                setTextColor(if (isSystemDark()) Color.WHITE else Color.BLACK)
                background = roundedBackground(if (isSystemDark()) "#331C212A" else "#33FFFFFF")
                setOnClickListener { showOverflowMenu(this) }
            }
            addView(
                overflowButton,
                FrameLayout.LayoutParams(48.dp, 48.dp, Gravity.BOTTOM or Gravity.END).apply {
                    setMargins(0, 0, 8.dp, 12.dp)
                },
            )
        }
    }

    private fun loadScheduleSite() {
        val generation = ++loadGeneration
        val profile = selectedSchedule
        showingLocalScheduleFallback = false
        hideOfflineNotice()
        showLoading(true)
        ioExecutor.execute {
            val snapshot = runCatching { WebsiteCache.read(applicationContext, profile) }.getOrNull()
            runOnUiThread {
                if (isDestroyed || isFinishing || generation != loadGeneration) return@runOnUiThread
                if (snapshot == null) {
                    renderLocalScheduleFallback(getString(R.string.schedule_site_unavailable))
                } else {
                    loadedWebsite = LoadedWebsite(profile, snapshot)
                    webView.loadUrl(profile.offlineUrl)
                }
            }
        }
    }

    private fun renderLocalScheduleFallback(message: String) {
        val profile = selectedSchedule
        val generation = loadGeneration
        val dark = isSystemDark()
        showingLocalScheduleFallback = true
        ioExecutor.execute {
            val html = OfflineScheduleHtml.render(ScheduleCache.read(applicationContext, profile), dark)
            runOnUiThread {
                if (isDestroyed || isFinishing || generation != loadGeneration) return@runOnUiThread
                showLoading(false)
                webView.loadDataWithBaseURL(profile.offlineUrl, html, "text/html", "UTF-8", null)
                showOfflineNotice(message)
            }
        }
    }

    private fun refreshScheduleData() {
        ScheduleSyncWorker.enqueueManual(applicationContext, selectedSchedule)
    }

    private fun showOverflowMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_REFRESH, 0, getString(R.string.menu_refresh))
            menu.add(
                0, MENU_SCHEDULE_SETTINGS, 1,
                getString(R.string.menu_schedule_settings, getString(selectedSchedule.labelRes)),
            )
            menu.add(0, MENU_ADD_WIDGET, 2, getString(R.string.menu_add_widget))
            menu.add(0, MENU_WIDGET_SETTINGS, 3, getString(R.string.menu_widget_settings))
            menu.add(0, MENU_ABOUT, 4, getString(R.string.menu_about))
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_REFRESH -> {
                        loadScheduleSite()
                        refreshScheduleData()
                        Toast.makeText(
                            this@MainActivity,
                            R.string.refreshing_schedule,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }

                    MENU_ADD_WIDGET -> requestWidgetPin()
                    MENU_SCHEDULE_SETTINGS -> showScheduleSettings()
                    MENU_WIDGET_SETTINGS -> openWidgetSettings()
                    MENU_ABOUT -> AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.about_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                true
            }
            show()
        }
    }

    private fun showScheduleSettings() {
        val profiles = ScheduleProfile.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.schedule_settings_title)
            .setSingleChoiceItems(
                profiles.map { getString(it.labelRes) }.toTypedArray(),
                profiles.indexOf(selectedSchedule),
            ) { dialog, index ->
                val profile = profiles[index]
                if (profile != selectedSchedule) {
                    SchedulePreferences.select(applicationContext, profile)
                    applySchedule(profile)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applySchedule(profile: ScheduleProfile) {
        selectedSchedule = profile
        webView.stopLoading()
        webView.clearHistory()
        loadScheduleSite()
        // Redraw all widget styles from this profile's saved/bundled data immediately.
        WidgetUpdateCoordinator.updateAll(applicationContext)
        refreshScheduleData()
    }

    private fun requestWidgetPin() {
        val provider = ComponentName(this, WidgetProviderRegistry.entries.first().providerClass)
        val requested = AppWidgetManager.getInstance(this).requestPinAppWidget(provider, null, null)
        if (!requested) {
            Toast.makeText(
                this,
                R.string.use_widget_picker,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun openWidgetSettings() {
        val manager = AppWidgetManager.getInstance(this)
        val widgetIds = WidgetProviderRegistry.entries
            .flatMap { entry -> manager.getAppWidgetIds(ComponentName(this, entry.providerClass)).toList() }
        when {
            widgetIds.isEmpty() -> Toast.makeText(
                this,
                getString(R.string.widget_settings_none),
                Toast.LENGTH_SHORT,
            ).show()

            widgetIds.size == 1 -> configureWidget(widgetIds.single())
            else -> AlertDialog.Builder(this)
                .setTitle(R.string.widget_settings_title)
                .setItems(
                    widgetIds.indices.map { index ->
                        getString(R.string.widget_settings_item, index + 1)
                    }.toTypedArray(),
                ) { _, index -> configureWidget(widgetIds[index]) }
                .show()
        }
    }

    private fun configureWidget(appWidgetId: Int) {
        startActivity(
            Intent(this, WidgetConfigurationActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(WidgetConfigurationActivity.EXTRA_RECONFIGURE, true),
        )
    }

    private fun showOfflineNotice(message: String) {
        offlineMessage.text = message
        offlineCard.visibility = View.VISIBLE
    }

    private fun hideOfflineNotice() {
        offlineCard.visibility = View.GONE
    }

    private fun showLoading(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun openExternally(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            showOfflineNotice(getString(R.string.no_app_to_open_link))
        }
    }

    private fun isTrustedScheduleUrl(uri: Uri): Boolean =
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(selectedSchedule.offlineHost, ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443)

    private fun isSystemDark(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun applySystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
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

    private fun roundedBackground(color: String): GradientDrawable =
        GradientDrawable().apply {
            setColor(color.toColorInt())
            cornerRadius = 20.dp.toFloat()
        }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()

    private inner class ScheduleWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
            val website = loadedWebsite
            val uri = request.url
            val url = if (website != null && uri.scheme == "https" &&
                uri.host == website.profile.offlineHost && (uri.port == -1 || uri.port == 443)) {
                val path = uri.encodedPath.orEmpty().ifEmpty { "/" }
                if (path == "/" || path == "/index.html") website.profile.siteUrl
                else website.profile.siteUrl.trimEnd('/') + path +
                    (uri.encodedQuery?.let { "?$it" } ?: "")
            } else uri.toString().substringBefore('#')
            val resource = website?.snapshot?.resources?.get(url)
            // All subresources come from the snapshot; WebView never downloads a partial revision.
            if (resource == null || request.method != "GET" || url.endsWith("/sw.js")) {
                return WebResourceResponse(
                    "text/plain", "UTF-8", 404, "Not Found", emptyMap(),
                    ByteArrayInputStream(ByteArray(0)),
                )
            }
            val bytes = if (resource.mimeType == "text/html") {
                WebsiteSnapshot.htmlForWebView(resource)
            } else resource.bytes
            return WebResourceResponse(
                resource.mimeType, if (resource.mimeType.startsWith("text/") ||
                    resource.mimeType.contains("json") || resource.mimeType.contains("javascript")) "UTF-8" else null,
                200, "OK", mapOf(
                    "Cache-Control" to "no-store",
                    "Access-Control-Allow-Origin" to "*",
                    "Content-Security-Policy" to "worker-src 'none'",
                ), ByteArrayInputStream(bytes),
            )
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (isTrustedScheduleUrl(request.url)) return false
            if (request.isForMainFrame) openExternally(request.url)
            return request.isForMainFrame
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (!isTrustedScheduleUrl(url.toUri())) return
            showLoading(false)
            if (!showingLocalScheduleFallback) {
                hideOfflineNotice()
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame && isTrustedScheduleUrl(request.url)) {
                renderLocalScheduleFallback(getString(R.string.schedule_site_unavailable))
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            if (request.isForMainFrame && isTrustedScheduleUrl(request.url) && errorResponse.statusCode >= 400) {
                renderLocalScheduleFallback(getString(R.string.schedule_site_unavailable))
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // Never train users to bypass a certificate warning inside a schedule app.
            handler.cancel()
            if (isTrustedScheduleUrl(error.url.toUri())) {
                renderLocalScheduleFallback(getString(R.string.ssl_error_saved_data))
            }
        }
    }
}
