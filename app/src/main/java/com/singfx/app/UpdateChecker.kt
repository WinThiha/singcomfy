package com.singfx.app

import android.content.Context
import com.singfx.app.yt.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class AppUpdate(val version: String, val pageUrl: String, val apkUrl: String?, val notes: String)

/** Checks GitHub Releases for a newer SingFX version (the app isn't on a store, so it updates itself this way). */
object UpdateChecker {
    private const val PREFS = "updates"
    private const val MIN_INTERVAL_MS = 6 * 60 * 60 * 1000L

    suspend fun check(context: Context, force: Boolean = false): AppUpdate? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("lastCheck", 0) < MIN_INTERVAL_MS) return@withContext null
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                prefs.edit().putLong("lastCheck", now).apply()
                val json = JSONObject(resp.body?.string() ?: return@withContext null)
                val tag = json.optString("tag_name").removePrefix("v")
                if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@withContext null
                if (!force && prefs.getString("skipped", null) == tag) return@withContext null
                val assets = json.optJSONArray("assets")
                var apk: String? = null
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
                }
                AppUpdate(tag, json.optString("html_url"), apk, json.optString("body").take(600))
            }
        } catch (e: Exception) {
            null
        }
    }

    fun skip(context: Context, version: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("skipped", version).apply()

    /** Compares dotted version strings numerically, e.g. 1.10.0 > 1.9.3. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
