package com.istarvin.classschedule2.data

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** One complete website revision. Readers keep this immutable snapshot for an entire page load. */
internal class WebsiteSnapshot(val resources: Map<String, Resource>) {
    data class Resource(val mimeType: String, val bytes: ByteArray)

    fun scheduleJson(profile: ScheduleProfile): String = ScheduleSourceParser.parse(
        resources.getValue(if (profile == ScheduleProfile.ALVIN) profile.remoteUrl else profile.siteUrl)
            .bytes.toString(Charsets.UTF_8),
    )

    fun validate(profile: ScheduleProfile) {
        require(resources.size in 1..MAX_RESOURCES)
        require(resources.values.sumOf { it.bytes.size.toLong() } <= MAX_TOTAL_BYTES)
        require(resources.keys.all { isAllowedUrl(it, profile) })
        val html = resources.getValue(profile.siteUrl).bytes.toString(Charsets.UTF_8)
        require(html.contains("<html", ignoreCase = true) && html.contains("renderHero")) {
            "Not a schedule website"
        }
        scheduleJson(profile)
        resources.forEach { (url, resource) ->
            require(resource.bytes.isNotEmpty() && resource.bytes.size <= MAX_RESOURCE_BYTES)
            require(dependencies(url, resource).all { it in resources }) { "Website has missing assets: $url" }
        }
        require("${profile.siteUrl}sw.js" in resources)
    }

    fun encode(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val manifest = JSONObject()
            resources.entries.forEachIndexed { index, (url, resource) ->
                val path = "resources/$index"
                manifest.put(url, JSONObject().put("path", path).put("mimeType", resource.mimeType))
                zip.putNextEntry(ZipEntry(path))
                zip.write(resource.bytes)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_RESOURCE_BYTES = 2_000_000
        const val MAX_TOTAL_BYTES = 8_000_000L
        const val MAX_RESOURCES = 128
        // Keep downloads deterministic, including the Google Fonts response used by the build tool.
        const val DOWNLOAD_USER_AGENT = "Class-Schedule-Offline/1.0"

        fun decode(bytes: ByteArray): WebsiteSnapshot {
            val files = mutableMapOf<String, ByteArray>()
            var total = 0L
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(files.size <= MAX_RESOURCES && entry.name !in files)
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_RESOURCE_BYTES)
                        output.write(buffer, 0, count)
                    }
                    val content = output.toByteArray()
                    total += content.size
                    require(total <= MAX_TOTAL_BYTES)
                    files[entry.name] = content
                }
            }
            val manifest = JSONObject(files.getValue("manifest.json").toString(Charsets.UTF_8))
            val resources = manifest.keys().asSequence().associateWith { url ->
                val item = manifest.getJSONObject(url)
                Resource(item.getString("mimeType"), files.getValue(item.getString("path")))
            }
            return WebsiteSnapshot(resources)
        }

        fun isAllowedUrl(url: String, profile: ScheduleProfile): Boolean = try {
            val uri = URI(url)
            uri.scheme == "https" && (uri.port == -1 || uri.port == 443) && uri.userInfo == null &&
                uri.host in setOf(profile.host, "fonts.googleapis.com", "fonts.gstatic.com")
        } catch (_: Exception) {
            false
        }

        fun resolve(base: String, reference: String): String? {
            val value = reference.trim().replace("&amp;", "&")
            if (value.isEmpty() || value.startsWith("#") || value.startsWith("data:")) return null
            val uri = URI(base).resolve(value).normalize()
            return uri.toString().substringBefore('#')
        }

        /** Static dependencies used by these two sites: HTML, CSS, and web-manifest icons. */
        fun dependencies(url: String, resource: Resource): Set<String> {
            val text = resource.bytes.toString(Charsets.UTF_8)
            val references = mutableListOf<String>()
            when (resource.mimeType) {
                "text/html" -> {
                    Regex("""<(?:link|script|img|source)\b[^>]*>""", RegexOption.IGNORE_CASE)
                        .findAll(text).forEach { match ->
                            val tag = match.value
                            if (!Regex("""\brel\s*=\s*["'](?:preconnect|dns-prefetch)["']""", RegexOption.IGNORE_CASE)
                                    .containsMatchIn(tag)) {
                                Regex("""\b(?:src|href)\s*=\s*["']([^"']+)["']""")
                                    .findAll(tag).forEach { references += it.groupValues[1] }
                            }
                        }
                    references += cssReferences(text)
                }
                "text/css" -> references += cssReferences(text)
                "application/manifest+json" -> {
                    val icons = JSONObject(text).optJSONArray("icons")
                    if (icons != null) for (index in 0 until icons.length()) {
                        references += icons.getJSONObject(index).getString("src")
                    }
                }
            }
            return references.mapNotNull { resolve(url, it) }.toSet()
        }

        private fun cssReferences(text: String): List<String> =
            Regex("""url\(\s*["']?([^\s"')]+)["']?\s*\)""").findAll(text)
                .map { it.groupValues[1] }.toList()

        /** Native sync owns caching; a second service-worker cache could hide newer saved revisions. */
        fun htmlForWebView(resource: Resource): ByteArray = resource.bytes.toString(Charsets.UTF_8)
            .replace("if (\"serviceWorker\" in navigator)", "if (false && \"serviceWorker\" in navigator)")
            .toByteArray(Charsets.UTF_8)
    }
}
