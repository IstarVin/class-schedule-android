package com.istarvin.classschedule2.data

import android.content.Context
import android.util.AtomicFile
import androidx.annotation.WorkerThread
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Bundled full websites, with complete downloaded revisions stored atomically in internal storage. */
internal object WebsiteCache {
    private val locks = ScheduleProfile.entries.associateWith { Any() }
    private val diskLocks = ScheduleProfile.entries.associateWith { Any() }

    private fun file(context: Context, profile: ScheduleProfile) =
        File(context.filesDir, "website_${profile.id}.zip")

    @WorkerThread
    fun read(context: Context, profile: ScheduleProfile): WebsiteSnapshot {
        val saved = runCatching {
            val bytes = synchronized(diskLocks.getValue(profile)) {
                AtomicFile(file(context, profile)).readFully()
            }
            WebsiteSnapshot.decode(bytes).also { it.validate(profile) }
        }.getOrNull()
        return saved ?: readBundled(context, profile)
    }

    private fun readBundled(context: Context, profile: ScheduleProfile): WebsiteSnapshot {
        val root = "websites/${profile.id}"
        val manifest = context.assets.open("$root/manifest.json").bufferedReader().use { it.readText() }
        val entries = org.json.JSONObject(manifest)
        return WebsiteSnapshot(entries.keys().asSequence().associateWith { url ->
            val item = entries.getJSONObject(url)
            WebsiteSnapshot.Resource(
                item.getString("mimeType"),
                context.assets.open("$root/${item.getString("path")}").use { it.readBytes() },
            )
        }).also { it.validate(profile) }
    }

    @WorkerThread
    fun refresh(context: Context, profile: ScheduleProfile): Boolean = synchronized(locks.getValue(profile)) {
        try {
            val resources = linkedMapOf<String, WebsiteSnapshot.Resource>()
            val pending = ArrayDeque<String>()
            pending += profile.siteUrl
            pending += "${profile.siteUrl}sw.js"
            if (profile == ScheduleProfile.ALVIN) pending += profile.remoteUrl
            var total = 0L
            while (pending.isNotEmpty()) {
                val url = pending.removeFirst()
                if (url in resources) continue
                require(resources.size < WebsiteSnapshot.MAX_RESOURCES)
                require(WebsiteSnapshot.isAllowedUrl(url, profile)) { "Unsupported website asset: $url" }
                val resource = download(url)
                total += resource.bytes.size
                require(total <= WebsiteSnapshot.MAX_TOTAL_BYTES)
                resources[url] = resource
                pending.addAll(WebsiteSnapshot.dependencies(url, resource))
            }
            val snapshot = WebsiteSnapshot(resources).also { it.validate(profile) }
            // The same validated source supplies widgets, including Sue's inline schedule tables.
            ScheduleCache.saveWebsiteSchedule(context, profile, snapshot.scheduleJson(profile))
            val bytes = snapshot.encode()
            synchronized(diskLocks.getValue(profile)) {
                val destination = AtomicFile(file(context, profile))
                val output = destination.startWrite()
                try {
                    output.write(bytes)
                    destination.finishWrite(output)
                } catch (error: Exception) {
                    destination.failWrite(output)
                    throw error
                }
            }
            true
        } catch (_: Exception) {
            // No partially downloaded page or asset can replace a usable offline revision.
            false
        }
    }

    private fun download(url: String): WebsiteSnapshot.Resource {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("User-Agent", WebsiteSnapshot.DOWNLOAD_USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Website returned HTTP ${connection.responseCode}")
            }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= WebsiteSnapshot.MAX_RESOURCE_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val mimeType = connection.contentType?.substringBefore(';')?.trim()
                ?: throw IOException("Website asset has no content type")
            return WebsiteSnapshot.Resource(mimeType, bytes)
        } finally {
            connection.disconnect()
        }
    }
}
