package com.istarvin.classschedule2

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.istarvin.classschedule2.data.SchedulePreferences
import com.istarvin.classschedule2.data.ScheduleProfile
import com.istarvin.classschedule2.data.WebsiteCache
import com.istarvin.classschedule2.data.WebsiteSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Run with device networking disabled to cover installation before the first website visit. */
@RunWith(AndroidJUnit4::class)
class OfflineWebsiteTest {
    @Test
    fun downloadedRevisionIsPreferredAndCorruptRevisionFallsBackToBundle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ScheduleProfile.entries.forEach { profile ->
            val saved = File(context.filesDir, "website_${profile.id}.zip")
            saved.delete()
            try {
                val bundled = WebsiteCache.read(context, profile)
                val original = bundled.resources.getValue(profile.siteUrl)
                val downloaded = WebsiteSnapshot.Resource(original.mimeType,
                    original.bytes.toString(Charsets.UTF_8)
                        .replace("<html lang=\"en\">", "<html lang=\"en\" data-saved-revision=\"true\">")
                        .toByteArray())
                saved.writeBytes(WebsiteSnapshot(bundled.resources + (profile.siteUrl to downloaded)).encode())
                assertTrue(WebsiteCache.read(context, profile).resources.getValue(profile.siteUrl)
                    .bytes.toString(Charsets.UTF_8).contains("data-saved-revision"))
                saved.writeText("interrupted or corrupt download")
                val fallback = WebsiteCache.read(context, profile)
                fallback.validate(profile)
                assertEquals(original.bytes.toString(Charsets.UTF_8),
                    fallback.resources.getValue(profile.siteUrl).bytes.toString(Charsets.UTF_8))
            } finally {
                saved.delete()
            }
        }
    }

    @Test
    fun bothBundledWebsitesRenderAndRemainInteractiveWithoutWebViewNetworking() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousProfile = SchedulePreferences.selected(context)
        try {
            ScheduleProfile.entries.forEach { profile ->
                SchedulePreferences.select(context, profile)
                File(context.filesDir, "website_${profile.id}.zip").delete()
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    waitForPage(scenario)
                    scenario.onActivity { activity ->
                        findWebView(activity.window.decorView).apply {
                            settings.blockNetworkLoads = true
                            loadUrl(profile.offlineUrl)
                        }
                    }
                    waitForPage(scenario)
                    assertEquals("\"${profile.offlineUrl}\"", javascript(scenario, "location.href"))
                    assertEquals("true", javascript(scenario,
                        "document.querySelector('#tabs').children.length >= 5"))
                    assertEquals("true", javascript(scenario, """
                        (() => {
                            document.querySelector('#timetableViewButton').click();
                            return document.querySelector('#timetableViewButton').getAttribute('aria-pressed') === 'true'
                                && document.querySelector('#panel').children.length > 0;
                        })()
                    """.trimIndent()))
                    // This fails if CSS loaded but the actual packaged font requests were lost.
                    assertEquals("true", javascript(scenario,
                        "Array.from(document.fonts).some(f => f.family.includes('IBM Plex') && f.status === 'loaded')"))
                }
            }
        } finally {
            SchedulePreferences.select(context, previousProfile)
        }
    }

    private fun waitForPage(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            if (javascript(scenario,
                "!!document.querySelector('#tabs') && document.querySelector('#tabs').children.length > 0 && document.fonts.status === 'loaded'"
            ) == "true") return
            Thread.sleep(100)
        }
        throw AssertionError("The full saved website did not render")
    }

    private fun javascript(scenario: ActivityScenario<MainActivity>, source: String): String {
        val completed = CountDownLatch(1)
        var result = ""
        scenario.onActivity { activity ->
            findWebView(activity.window.decorView).evaluateJavascript(source) {
                result = it
                completed.countDown()
            }
        }
        assertTrue("JavaScript callback timed out", completed.await(5, TimeUnit.SECONDS))
        return result
    }

    private fun findWebView(view: View): WebView {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            runCatching { findWebView(view.getChildAt(index)) }.getOrNull()?.let { return it }
        }
        throw IllegalArgumentException("No WebView in view hierarchy")
    }
}
