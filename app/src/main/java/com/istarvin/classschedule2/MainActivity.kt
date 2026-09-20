package com.istarvin.classschedule2

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import com.istarvin.classschedule2.data.OfflineScheduleHtml
import com.istarvin.classschedule2.data.ScheduleCache
import com.istarvin.classschedule2.data.ScheduleSyncWorker
import com.istarvin.classschedule2.widget.WidgetConfigurationActivity
import com.istarvin.classschedule2.widget.WidgetProviderRegistry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import androidx.core.net.toUri

/** A deliberately small native shell around the schedule's trusted web experience. */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val SCHEDULE_URL = "https://ajschedule.pages.dev/"
        private const val SCHEDULE_HOST = "ajschedule.pages.dev"
        private const val LIGHT_SURFACE = "#F3F1EA"
        private const val DARK_SURFACE = "#15181E"
        private const val MENU_REFRESH = 1
        private const val MENU_ADD_WIDGET = 2
        private const val MENU_WIDGET_SETTINGS = 3
        private const val MENU_ABOUT = 4
    }

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var webView: WebView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var offlineCard: LinearLayout
    private lateinit var offlineMessage: TextView
    private lateinit var retryButton: TextView
    private var cacheFallbackAttempted = false
    private var showingLocalScheduleFallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        applySystemBarAppearance(isSystemDark())
        setContentView(createContentView())
        configureWebView()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        // Let Android complete the final predictive-back animation.
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )

        // This is also the initial offline source for widgets.
        ScheduleCache.seedIfMissing(applicationContext)
        ScheduleSyncWorker.ensurePeriodic(applicationContext)
        refreshScheduleData()

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            loadScheduleSite()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        applySystemBarAppearance(isSystemDark())
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
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
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            userAgentString = "$userAgentString AJ-Schedule-Android/1.0"
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
            retryButton = TextView(this@MainActivity).apply {
                setTextColor("#A7F3D0".toColorInt())
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12.dp, 0, 0)
                text = getString(R.string.try_again)
                contentDescription = getString(R.string.retry_loading_schedule)
                setOnClickListener { loadScheduleSite(forceNetwork = true) }
            }
            offlineCard.addView(
                offlineMessage,
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
                FrameLayout.LayoutParams(36.dp, 36.dp, Gravity.BOTTOM or Gravity.END).apply {
                    setMargins(0, 0, 8.dp, 12.dp)
                },
            )
        }
    }

    private fun loadScheduleSite(forceNetwork: Boolean = false) {
        cacheFallbackAttempted = false
        showingLocalScheduleFallback = false
        hideOfflineNotice()
        showLoading(true)
        webView.settings.cacheMode = when {
            forceNetwork -> WebSettings.LOAD_NO_CACHE
            isNetworkAvailable() -> WebSettings.LOAD_DEFAULT
            else -> WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
        webView.loadUrl(SCHEDULE_URL)
    }

    private fun showCachedScheduleFallback() {
        if (cacheFallbackAttempted) {
            renderLocalScheduleFallback(getString(R.string.schedule_site_unavailable))
            return
        }
        cacheFallbackAttempted = true
        showOfflineNotice(getString(R.string.offline_loading_saved_schedule))
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        webView.loadUrl(SCHEDULE_URL)
    }

    private fun renderLocalScheduleFallback(message: String) {
        showingLocalScheduleFallback = true
        showLoading(false)
        webView.loadDataWithBaseURL(
            SCHEDULE_URL,
            OfflineScheduleHtml.render(ScheduleCache.read(applicationContext), isSystemDark()),
            "text/html",
            "UTF-8",
            null,
        )
        showOfflineNotice(message)
    }

    private fun refreshScheduleData() {
        ioExecutor.execute {
            when (ScheduleCache.refresh(applicationContext)) {
                is ScheduleCache.RefreshResult.Updated,
                is ScheduleCache.RefreshResult.NotModified
                -> sendBroadcast(
                    Intent(ScheduleCache.ACTION_SCHEDULE_UPDATED)
                        .setPackage(packageName),
                )

                is ScheduleCache.RefreshResult.Failed -> Unit
            }
        }
    }

    private fun showOverflowMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_REFRESH, 0, getString(R.string.menu_refresh))
            menu.add(0, MENU_ADD_WIDGET, 1, getString(R.string.menu_add_widget))
            menu.add(0, MENU_WIDGET_SETTINGS, 2, getString(R.string.menu_widget_settings))
            menu.add(0, MENU_ABOUT, 3, getString(R.string.menu_about))
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_REFRESH -> {
                        loadScheduleSite(forceNetwork = true)
                        refreshScheduleData()
                        Toast.makeText(
                            this@MainActivity,
                            R.string.refreshing_schedule,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }

                    MENU_ADD_WIDGET -> requestWidgetPin()
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

    private fun isNetworkAvailable(): Boolean {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isTrustedScheduleUrl(uri: Uri): Boolean =
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(SCHEDULE_HOST, ignoreCase = true) &&
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

    private fun roundedBackground(color: String): GradientDrawable =
        GradientDrawable().apply {
            setColor(color.toColorInt())
            cornerRadius = 20.dp.toFloat()
        }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()

    private inner class ScheduleWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (isTrustedScheduleUrl(request.url)) return false
            if (request.isForMainFrame) openExternally(request.url)
            return request.isForMainFrame
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            showLoading(false)
            if (!showingLocalScheduleFallback && isTrustedScheduleUrl(url.toUri()) && isNetworkAvailable()) {
                hideOfflineNotice()
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) showCachedScheduleFallback()
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            if (request.isForMainFrame && errorResponse.statusCode >= 500) showCachedScheduleFallback()
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // Never train users to bypass a certificate warning inside a schedule app.
            handler.cancel()
            renderLocalScheduleFallback(
                getString(R.string.ssl_error_saved_data),
            )
        }
    }
}
