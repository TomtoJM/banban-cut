package app.banban.cut

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A build newer than this one, published on the repo's Releases page by CI. */
data class Update(val versionCode: Int, val apkUrl: String, val notes: String)

/** What the last update check found, so the screen can always say something. */
sealed interface UpdateState {
    data object Checking : UpdateState
    data object Latest : UpdateState
    data object Failed : UpdateState
    data class Available(val update: Update) : UpdateState
    data class Downloading(val update: Update, val percent: Int) : UpdateState
    data class Ready(val update: Update, val apk: File) : UpdateState
}

object Updater {
    /** Releases are tagged v<versionCode>. Call off the main thread. */
    fun check(): UpdateState = runCatching {
        val conn = URL("https://api.github.com/repos/${BuildConfig.REPO}/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.useCaches = false
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val code = o.getString("tag_name").removePrefix("v").toInt()
        if (code <= BuildConfig.VERSION_CODE) return UpdateState.Latest
        val assets = o.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .first { it.getString("name").endsWith(".apk") }
        UpdateState.Available(Update(code, apk.getString("browser_download_url"), o.optString("body")))
    }.getOrDefault(UpdateState.Failed)

    /** Downloads the APK into the app's cache, reporting 0..100. Call off the main thread. */
    fun download(context: Context, update: Update, onProgress: (Int) -> Unit): File? = runCatching {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "banban-cut-v${update.versionCode}.apk")
        val conn = URL(update.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    val p = if (total > 0) (done * 100 / total).toInt() else 0
                    if (p != last) { last = p; onProgress(p) }
                }
            }
        }
        out
    }.getOrNull()

    /**
     * Hands the APK to Android's installer, which asks "update this app?".
     * The first time, Android needs permission for this app to install updates; we open that setting instead.
     */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
