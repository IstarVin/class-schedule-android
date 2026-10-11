package com.istarvin.classschedule2.data

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WebsiteSnapshotTest {
    private fun bundled(profile: ScheduleProfile): WebsiteSnapshot {
        val root = "websites/${profile.id}"
        fun bytes(path: String) = requireNotNull(javaClass.classLoader?.getResourceAsStream("$root/$path"))
            .use { it.readBytes() }
        val manifest = JSONObject(bytes("manifest.json").toString(Charsets.UTF_8))
        return WebsiteSnapshot(manifest.keys().asSequence().associateWith { url ->
            val item = manifest.getJSONObject(url)
            WebsiteSnapshot.Resource(item.getString("mimeType"), bytes(item.getString("path")))
        })
    }

    @Test
    fun `both packaged websites contain valid schedules and every offline dependency`() {
        ScheduleProfile.entries.forEach { profile ->
            val snapshot = bundled(profile)
            snapshot.validate(profile)
            assertTrue(JSONObject(snapshot.scheduleJson(profile)).getJSONArray("events").length() > 0)
            assertTrue(snapshot.resources.values.any { it.mimeType.startsWith("font/") })
            assertTrue(snapshot.resources.keys.any { it.endsWith("icons/icon-512.svg") })
        }
    }

    @Test
    fun `downloaded snapshot round trip preserves exact asset bytes`() {
        val original = bundled(ScheduleProfile.SUE)
        val decoded = WebsiteSnapshot.decode(original.encode())
        decoded.validate(ScheduleProfile.SUE)
        assertEquals(original.resources.keys, decoded.resources.keys)
        original.resources.forEach { (url, resource) ->
            assertEquals(resource.mimeType, decoded.resources.getValue(url).mimeType)
            assertArrayEquals(resource.bytes, decoded.resources.getValue(url).bytes)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing font rejects an incomplete revision`() {
        val snapshot = bundled(ScheduleProfile.ALVIN)
        val font = snapshot.resources.keys.first { it.startsWith("https://fonts.gstatic.com/") }
        WebsiteSnapshot(snapshot.resources - font).validate(ScheduleProfile.ALVIN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid schedule rejects a website revision`() {
        val profile = ScheduleProfile.ALVIN
        val snapshot = bundled(profile)
        WebsiteSnapshot(snapshot.resources + (profile.remoteUrl to WebsiteSnapshot.Resource(
            "application/json", "{\"subjects\":{},\"events\":[{\"day\":1,\"start\":400,\"end\":300}]}".toByteArray(),
        ))).validate(profile)
    }

    @Test
    fun `HTML dependencies ignore preconnect and decode font query entities`() {
        val resource = WebsiteSnapshot.Resource("text/html", """
            <link rel="preconnect" href="https://fonts.gstatic.com">
            <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=A&amp;display=swap">
            <script src="./app.js"></script><img src="data:image/png;base64,abc">
            <style>body { background: url('./background.svg'); }</style>
        """.toByteArray())
        assertEquals(setOf(
            "https://fonts.googleapis.com/css2?family=A&display=swap",
            "https://ajschedule.pages.dev/app.js",
            "https://ajschedule.pages.dev/background.svg",
        ), WebsiteSnapshot.dependencies(ScheduleProfile.ALVIN.siteUrl, resource))
    }

    @Test
    fun `asset downloads cannot switch profiles hosts schemes or ports`() {
        val profile = ScheduleProfile.ALVIN
        assertTrue(WebsiteSnapshot.isAllowedUrl(profile.siteUrl, profile))
        assertTrue(WebsiteSnapshot.isAllowedUrl("https://fonts.gstatic.com/font.ttf", profile))
        listOf(ScheduleProfile.SUE.siteUrl, "http://ajschedule.pages.dev/", "file:///etc/passwd",
            "https://ajschedule.pages.dev:8443/", "https://ajschedule.pages.dev.attacker.test/",
            "https://user@ajschedule.pages.dev/").forEach {
            assertFalse(it, WebsiteSnapshot.isAllowedUrl(it, profile))
        }
        assertNotEquals(profile.offlineHost, ScheduleProfile.SUE.offlineHost)
    }

    @Test
    fun `native pages avoid a competing service worker cache without modifying packaged source`() {
        ScheduleProfile.entries.forEach { profile ->
            val resource = bundled(profile).resources.getValue(profile.siteUrl)
            val original = resource.bytes.toString(Charsets.UTF_8)
            assertTrue(original.contains("if (\"serviceWorker\" in navigator)"))
            val served = WebsiteSnapshot.htmlForWebView(resource).toString(Charsets.UTF_8)
            assertTrue(served.contains("if (false && \"serviceWorker\" in navigator)"))
            assertFalse(served.contains("if (\"serviceWorker\" in navigator)"))
            assertEquals(original, resource.bytes.toString(Charsets.UTF_8))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized downloaded archive entry is rejected before full decompression`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use {
            it.putNextEntry(ZipEntry("oversized"))
            it.write(ByteArray(WebsiteSnapshot.MAX_RESOURCE_BYTES + 1))
            it.closeEntry()
        }
        WebsiteSnapshot.decode(output.toByteArray())
    }
}
