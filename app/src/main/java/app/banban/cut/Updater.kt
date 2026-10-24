package app.banban.cut

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
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
    /** Couldn't reach GitHub to ask. */
    data object Failed : UpdateState
    /** Got as far as installing, and Android said no; [reason] says why. */
    data class InstallFailed(val reason: String, val blocked: Boolean = false) : UpdateState
    /** Waiting for the person to allow 반반컷 to install updates. */
    data class NeedsPermission(val update: Update) : UpdateState
    data class Available(val update: Update) : UpdateState
    data class Downloading(val update: Update, val percent: Int) : UpdateState
    data class Installing(val update: Update) : UpdateState
}

object Updater {
    /**
     * Releases are tagged v<versionCode>. Asks github.com's own "latest release" page first: the
     * API allows only 60 questions an hour per network, and phone carriers share one address among
     * many people, so the API often says no. Call off the main thread.
     */
    fun check(): UpdateState = runCatching { fromReleasePage() }
        .recoverCatching { fromApi() }
        .getOrDefault(UpdateState.Failed)

    private fun found(code: Int, apkUrl: String) =
        if (code <= BuildConfig.VERSION_CODE) UpdateState.Latest else UpdateState.Available(Update(code, apkUrl, ""))

    /** github.com/<repo>/releases/latest answers with a jump to …/tag/v<code>. */
    private fun fromReleasePage(): UpdateState {
        val conn = URL("https://github.com/${BuildConfig.REPO}/releases/latest").openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.useCaches = false
        val tag = try { conn.getHeaderField("Location") } finally { conn.disconnect() }
            ?.substringAfterLast("/tag/", "")?.takeIf { it.startsWith("v") } ?: error("no tag")
        val code = tag.removePrefix("v").toInt()
        // CI names the APK after its build number.
        return found(code, "https://github.com/${BuildConfig.REPO}/releases/download/$tag/banban-cut-$tag.apk")
    }

    private fun fromApi(): UpdateState {
        val conn = URL("https://api.github.com/repos/${BuildConfig.REPO}/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.useCaches = false
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val code = o.getString("tag_name").removePrefix("v").toInt()
        val assets = o.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .first { it.getString("name").endsWith(".apk") }
        return found(code, apk.getString("browser_download_url"))
    }

    /** Android's answer to an install, in words someone can act on. */
    fun reason(status: Int, message: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "설치를 취소했어요. 다시 누르면 이어서 받아요."
        PackageInstaller.STATUS_FAILURE_BLOCKED ->
            "폰의 자동 차단이 설치를 막았어요. 아래 버튼으로 보안 설정을 열고 '자동 차단'을 끈 뒤 다시 해 주세요."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "저장 공간이 부족해요. 공간을 비우고 다시 해 주세요."
        PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "지금 앱과 새 버전의 서명이 달라서 설치할 수 없어요."
        else -> "설치하지 못했어요." + (message?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
    }

    const val ACTION_INSTALL_STATUS = "app.banban.cut.INSTALL_STATUS"

    /**
     * Streams the APK straight into Android's installer, the way store apps do: no file is saved on
     * the phone. Android asks to confirm each time; the person stays in charge of what gets installed.
     * Call off the main thread; false means the download failed.
     */
    fun downloadAndInstall(context: Context, update: Update, onProgress: (Int) -> Unit): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
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
