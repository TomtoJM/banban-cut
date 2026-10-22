package app.banban.cut

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One pair: a clip of the action on top, and a screen recording of checking it in the app below.
 * The recording's top part, where the check happens, fills the bottom half.
 */
data class Cut(
    val id: String = UUID.randomUUID().toString(),
    val topUri: Uri? = null,
    val topStartMs: Long = 0,
    val caption: String = "",
    val appUri: Uri? = null,
    val checkAtMs: Long = 0,
) {
    val ready get() = topUri != null && appUri != null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("topUri", topUri?.toString())
        .put("topStartMs", topStartMs)
        .put("caption", caption)
        .put("appUri", appUri?.toString())
        .put("checkAtMs", checkAtMs)

    companion object {
        fun fromJson(o: JSONObject) = Cut(
            id = o.getString("id"),
            topUri = o.optString("topUri").takeIf { it.isNotEmpty() && it != "null" }?.let(Uri::parse),
            topStartMs = o.optLong("topStartMs"),
            caption = o.optString("caption"),
            appUri = o.optString("appUri").takeIf { it.isNotEmpty() && it != "null" }?.let(Uri::parse),
            checkAtMs = o.optLong("checkAtMs"),
        )
    }
}

/** outro: add the closing "Link in bio" card. Off by default; reels loop better without it. */
data class Project(val hook: String, val cuts: List<Cut>, val outro: Boolean = false) {
    companion object {
        private const val PREFS = "project"
        private const val KEY = "current"

        /** The work in progress survives closing the app. */
        fun load(context: Context, defaultHook: String): Project {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return Project(defaultHook, emptyList())
            return runCatching {
                val o = JSONObject(raw)
                val arr = o.getJSONArray("cuts")
                Project(
                    o.optString("hook", defaultHook),
                    (0 until arr.length()).map { Cut.fromJson(arr.getJSONObject(it)) },
                    o.optBoolean("outro", false),
                )
            }.getOrDefault(Project(defaultHook, emptyList()))
        }

        fun save(context: Context, p: Project) {
            val arr = JSONArray().apply { p.cuts.forEach { put(it.toJson()) } }
            val raw = JSONObject().put("hook", p.hook).put("cuts", arr).put("outro", p.outro).toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
        }
    }
}
