package com.my.striptologbook.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val apkUrl: String,
    val fileName: String,
    val versionName: String
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()
    object NoUpdateAvailable : UpdateCheckResult()
    object InvalidResponse   : UpdateCheckResult()
    object NoResponse        : UpdateCheckResult()
}

object UpdateChecker {

    private const val TAG = "UpdateChecker"

    // GitHub repository (owner/repo)
    private const val GITHUB_REPO = "freedrafat/pilotLogbookFoss"
    private const val GITHUB_API_LATEST =
        "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    suspend fun checkForUpdate(context: Context): UpdateCheckResult =
        withContext(Dispatchers.IO) {

            if (!isNetworkAvailable(context)) {
                Log.d(TAG, "No internet")
                return@withContext UpdateCheckResult.NoResponse
            }

            try {
                val client = UnsafeHttpClient.getUnsafeOkHttpClient()
                val request = Request.Builder()
                    .url(GITHUB_API_LATEST)
                    .header("Accept", "application/vnd.github+json")
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.d(TAG, "Non-200 HTTP: ${response.code}")
                    return@withContext UpdateCheckResult.NoResponse
                }

                val responseBody = response.body.string()

                // HTTP 200 from here -> server reached. Further failures are
                // InvalidResponse, not NoResponse.
                val json = try {
                    JSONObject(responseBody)
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid JSON", e)
                    return@withContext UpdateCheckResult.InvalidResponse
                }

                // tag_name looks like "v1.0.0"
                val rawTag = json.optString("tag_name", "")
                if (rawTag.isEmpty()) {
                    return@withContext UpdateCheckResult.InvalidResponse
                }
                val remoteVersion = rawTag.removePrefix("v").trim()
                if (remoteVersion.isEmpty()) {
                    return@withContext UpdateCheckResult.InvalidResponse
                }

                // Find the first .apk asset
                val assets = json.optJSONArray("assets")
                    ?: return@withContext UpdateCheckResult.InvalidResponse
                var apkUrl: String? = null
                var apkName: String? = null
                for (i in 0 until assets.length()) {
                    val asset = try {
                        assets.getJSONObject(i)
                    } catch (e: Exception) {
                        continue
                    }
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotEmpty()) {
                        apkName = name
                        apkUrl = url
                        break
                    }
                }

                if (apkUrl == null || apkName == null) {
                    Log.d(TAG, "No APK asset in latest release")
                    return@withContext UpdateCheckResult.InvalidResponse
                }

                val currentVersion = getCurrentAppVersionName(context)
                    ?: return@withContext UpdateCheckResult.InvalidResponse

                if (!isNewerVersion(remoteVersion, currentVersion)) {
                    return@withContext UpdateCheckResult.NoUpdateAvailable
                }

                return@withContext UpdateCheckResult.UpdateAvailable(
                    UpdateInfo(apkUrl, apkName, remoteVersion)
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error checking update", e)
                return@withContext UpdateCheckResult.NoResponse
            }
        }

    /**
     * Compares two semantic versions like "1.2.0" vs "1.10.3".
     * Returns true if [remote] is strictly newer than [current].
     * Missing segments are treated as 0. Non-numeric segments stop
     * comparison and the shorter side is considered older.
     */
    private fun isNewerVersion(remote: String, current: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        val size = maxOf(r.size, c.size)
        for (i in 0 until size) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }

    private fun getCurrentAppVersionName(context: Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val v = packageInfo.versionName
            Log.d(TAG, "Current versionName: $v")
            v
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current app version", e)
            null
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}