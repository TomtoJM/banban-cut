package app.banban.cut

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A build newer than this one, published on the repo's Releases page by CI. */
data class Update(val versionCode: Int, val apkUrl: String, val notes: String)

object Updater {
    /** Releases are tagged v<versionCode>. Call off the main thread; null means up to date or offline. */
    fun check(): Update? = runCatching {
        val conn = URL("https://api.github.com/repos/${BuildConfig.REPO}/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val code = o.getString("tag_name").removePrefix("v").toInt()
        if (code <= BuildConfig.VERSION_CODE) return null
        val assets = o.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .first { it.getString("name").endsWith(".apk") }
        Update(code, apk.getString("browser_download_url"), o.optString("body"))
    }.getOrNull()
}
