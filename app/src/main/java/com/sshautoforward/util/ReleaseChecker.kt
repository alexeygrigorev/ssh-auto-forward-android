package com.sshautoforward.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val apkUrl: String,
)

class ReleaseChecker {
    companion object {
        private const val REPO = "alexeygrigorev/ssh-auto-forward-android"
        private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"
    }

    suspend fun check(currentVersion: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "ssh-auto-forward-android")

            if (conn.responseCode != 200) return@withContext null

            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tagName = json.getString("tag_name")

            if (!isNewer(currentVersion, tagName)) return@withContext null

            val htmlUrl = json.getString("html_url")
            val assets = json.optJSONArray("assets") ?: return@withContext null

            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith("-debug.apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (apkUrl.isEmpty()) return@withContext null

            ReleaseInfo(tagName, htmlUrl, apkUrl)
        } catch (_: Exception) {
            null
        }
    }

    private fun isNewer(current: String, remote: String): Boolean {
        val currentTag = current.substringBefore("-")
        val remoteTag = remote.removePrefix("v")
        val currentNums = currentTag.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val remoteNums = remoteTag.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(currentNums.size, remoteNums.size)) {
            val c = currentNums.getOrElse(i) { 0 }
            val r = remoteNums.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
