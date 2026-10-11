package com.istarvin.classschedule2.data

import android.content.Context
import android.util.AtomicFile
import androidx.annotation.WorkerThread
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import androidx.core.content.edit

/**
 * The single schedule-data handoff between the WebView shell, background sync,
 * and widgets. A bundled schedule lets a fresh install work before its first
 * successful network request; a refreshed copy is kept in [Context.filesDir].
 */
object ScheduleCache {
    const val ACTION_SCHEDULE_UPDATED = "com.istarvin.classschedule2.action.SCHEDULE_UPDATED"

    private const val KEY_ETAG = "etag"
    private const val KEY_LAST_UPDATED = "last_updated_millis"
    private const val MAX_SCHEDULE_BYTES = 1_000_000
    private const val CONNECT_TIMEOUT_MILLIS = 15_000
    private const val READ_TIMEOUT_MILLIS = 20_000
    private val refreshLocks = ScheduleProfile.entries.associateWith { Any() }

    /** The file widgets should read first, before falling back to the asset. */
    fun file(context: Context, profile: ScheduleProfile = SchedulePreferences.selected(context)): File =
        File(context.applicationContext.filesDir, profile.assetFileName)

    /**
     * Copies the packaged schedule only when no usable local copy exists. This
     * deliberately never overwrites a successfully refreshed schedule.
     */
    @Synchronized
    fun seedIfMissing(context: Context, profile: ScheduleProfile = SchedulePreferences.selected(context)): Boolean {
        val appContext = context.applicationContext
        val destination = file(appContext, profile)
        if (destination.isFile && destination.length() > 0L) return false

        return try {
            val bytes = appContext.assets.open(profile.assetFileName).use { it.readBoundedBytes() }
            ScheduleSourceParser.parse(bytes.toString(StandardCharsets.UTF_8))
            writeAtomically(destination, bytes)
            // A restored HTTP validator must not treat a newly seeded bundle as fresh data.
            appContext.getSharedPreferences(profile.metadataPreferencesName, Context.MODE_PRIVATE)
                .edit {
                    remove(KEY_ETAG)
                    remove(KEY_LAST_UPDATED)
                }
            true
        } catch (_: IOException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun read(context: Context, profile: ScheduleProfile = SchedulePreferences.selected(context)): String? {
        seedIfMissing(context, profile)
        return try {
            file(context, profile).readText(StandardCharsets.UTF_8)
        } catch (_: IOException) {
            null
        }
    }

    fun lastUpdatedMillis(context: Context, profile: ScheduleProfile = SchedulePreferences.selected(context)): Long =
        context.applicationContext
            .getSharedPreferences(profile.metadataPreferencesName, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATED, 0L)

    /**
     * Downloads the profile's source and replaces its local JSON cache only
     * after both its schema and complete bytes have been verified. Invoke this
     * from a worker or an executor, never the main thread.
     */
    @WorkerThread
    fun refresh(context: Context, profile: ScheduleProfile = SchedulePreferences.selected(context)): RefreshResult =
        synchronized(refreshLocks.getValue(profile)) { refreshProfile(context, profile) }

    private fun refreshProfile(context: Context, profile: ScheduleProfile): RefreshResult {
        val appContext = context.applicationContext
        seedIfMissing(appContext, profile)
        val preferences = appContext.getSharedPreferences(profile.metadataPreferencesName, Context.MODE_PRIVATE)
        var connection: HttpURLConnection? = null

        return try {
            connection = (URL(profile.remoteUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept", "application/json, text/html")
                preferences.getString(KEY_ETAG, null)?.let { setRequestProperty("If-None-Match", it) }
            }

            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    val now = System.currentTimeMillis()
                    preferences.edit { putLong(KEY_LAST_UPDATED, now) }
                    RefreshResult.NotModified(now)
                }

                in HttpURLConnection.HTTP_OK..299 -> {
                    val source = connection.inputStream.use { it.readBoundedBytes() }
                    val bytes = ScheduleSourceParser.parse(source.toString(StandardCharsets.UTF_8))
                        .toByteArray(StandardCharsets.UTF_8)
                    val now = System.currentTimeMillis()
                    // Disk readers only wait for the atomic write, never for a network request.
                    synchronized(this) {
                        writeAtomically(file(appContext, profile), bytes)
                        preferences.edit {
                            putLong(KEY_LAST_UPDATED, now)
                            val etag = connection.getHeaderField("ETag")
                            if (etag == null) remove(KEY_ETAG) else putString(KEY_ETAG, etag)
                        }
                    }
                    RefreshResult.Updated(bytes.size, now)
                }

                else -> RefreshResult.Failed("Schedule server returned HTTP $status")
            }
        } catch (error: IOException) {
            RefreshResult.Failed(error.message ?: "Unable to reach the schedule server")
        } catch (error: IllegalArgumentException) {
            RefreshResult.Failed(error.message ?: "The downloaded schedule was invalid")
        } catch (error: Exception) {
            RefreshResult.Failed(error.message ?: "The downloaded schedule was invalid")
        } finally {
            connection?.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun writeAtomically(destination: File, bytes: ByteArray) {
        val atomicFile = AtomicFile(destination)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(bytes)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: IOException) {
            output?.let(atomicFile::failWrite)
            throw error
        }
    }

    @Throws(IOException::class)
    private fun InputStream.readBoundedBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_SCHEDULE_BYTES) {
                throw IOException("Schedule is larger than $MAX_SCHEDULE_BYTES bytes")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    sealed class RefreshResult {
        data class Updated(val byteCount: Int, val refreshedAtMillis: Long) : RefreshResult()
        data class NotModified(val checkedAtMillis: Long) : RefreshResult()
        data class Failed(val reason: String) : RefreshResult()
    }
}
