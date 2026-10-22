package app.banban.cut

import android.content.Context
import android.graphics.Color
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Layout and timing for one reel. Bundled in assets; a newer copy can come from the repo. */
data class Template(
    val width: Int,
    val height: Int,
    val fps: Int,
    val cutSeconds: Double,
    val leadSeconds: Double,
    val panStartSeconds: Double,
    val panSeconds: Double,
    val endSeconds: Double,
    val safeTop: Int,
    val safeBottom: Int,
    val safeSide: Int,
    val timeY: Int,
    val timeSize: Int,
    val timeColor: Int,
    val hookY: Int,
    val hookSize: Int,
    val hookMaxLines: Int,
    val hookColor: Int,
    val hookText: String,
    val endTitle: String,
    val endSubtitle: String,
    val endBackground: Int,
    val endAccent: Int,
    val defaultPanToY: Float,
) {
    val halfHeight get() = height / 2

    companion object {
        private const val PREFS = "template"
        private const val KEY = "remote"

        fun parse(json: String): Template {
            val o = JSONObject(json)
            val safe = o.getJSONObject("safe")
            val time = o.getJSONObject("timeCaption")
            val hook = o.getJSONObject("hook")
            val end = o.getJSONObject("end")
            return Template(
                width = o.getInt("width"),
                height = o.getInt("height"),
                fps = o.getInt("fps"),
                cutSeconds = o.getDouble("cutSeconds"),
                leadSeconds = o.getDouble("leadSeconds"),
                panStartSeconds = o.getDouble("panStartSeconds"),
                panSeconds = o.getDouble("panSeconds"),
                endSeconds = o.getDouble("endSeconds"),
                safeTop = safe.getInt("top"),
                safeBottom = safe.getInt("bottom"),
                safeSide = safe.getInt("side"),
                timeY = time.getInt("y"),
                timeSize = time.getInt("size"),
                timeColor = Color.parseColor(time.getString("color")),
                hookY = hook.getInt("y"),
                hookSize = hook.getInt("size"),
                hookMaxLines = hook.getInt("maxLines"),
                hookColor = Color.parseColor(hook.getString("color")),
                hookText = hook.getString("text"),
                endTitle = end.getString("title"),
                endSubtitle = end.getString("subtitle"),
                endBackground = Color.parseColor(end.getString("background")),
                endAccent = Color.parseColor(end.getString("accent")),
                defaultPanToY = o.getDouble("defaultPanToY").toFloat(),
            )
        }

        /** The last template fetched from the repo if it parses, else the bundled one. */
        fun load(context: Context): Template {
            val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            if (cached != null) runCatching { return parse(cached) }
            val bundled = context.assets.open("template.json").bufferedReader().use { it.readText() }
            return parse(bundled)
        }

        /** Fetches the repo's template.json so layout tweaks land without a new APK. Call off the main thread. */
        fun refresh(context: Context): Template? {
            val url = "https://raw.githubusercontent.com/${BuildConfig.REPO}/main/app/src/main/assets/template.json"
            return runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val t = parse(body)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, body).apply()
                t
            }.getOrNull()
        }
    }
}
