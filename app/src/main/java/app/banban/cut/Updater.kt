package app.banban.cut

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
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
    data class Installing(val update: Update) : UpdateState
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

    const val ACTION_INSTALL_STATUS = "app.banban.cut.INSTALL_STATUS"

    /**
     * Streams the APK straight into Android's installer, the way store apps do: no file is saved on
     * the phone. Android then asks once to confirm (and, after the first time, may update silently).
     * Call off the main thread; false means the download failed.
     */
    fun downloadAndInstall(context: Context, update: Update, onProgress: (Int) -> Unit): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        return runCatching {
            installer.openSession(id).use { session ->
                val conn = URL(update.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    session.openWrite("base.apk", 0, if (total > 0) total else -1).use { output ->
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
                        session.fsync(output)
                    }
                }
                // Android reports back to MainActivity, which shows the confirm screen if one is needed.
                val status = Intent(context, MainActivity::class.java).setAction(ACTION_INSTALL_STATUS)
                val pending = PendingIntent.getActivity(
                    context, id, status, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            }
            true
        }.getOrElse {
            runCatching { installer.abandonSession(id) }
            false
        }
    }

    /** Older builds left a downloaded APK in the cache; clear it. */
    fun cleanUp(context: Context) {
        File(context.cacheDir, "updates").deleteRecursively()
    }
}
