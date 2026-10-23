package app.banban.cut

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One beat of the reel: a clip of the action fills the top cell, a screen recording of checking it
 * off in the app fills the bottom cell. Each cell plays one cut length from its start point.
 */
data class Cut(
    val id: String = UUID.randomUUID().toString(),
    val topUri: Uri? = null,
    val topStartMs: Long = 0,
    val appUri: Uri? = null,
    val appStartMs: Long = 0,
) {
    val ready get() = topUri != null && appUri != null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("topUri", topUri?.toString())
        .put("topStartMs", topStartMs)
        .put("appUri", appUri?.toString())
        .put("appStartMs", appStartMs)

    companion object {
        fun fromJson(o: JSONObject) = Cut(
            id = o.getString("id"),
            topUri = o.uri("topUri"),
            topStartMs = o.optLong("topStartMs"),
            appUri = o.uri("appUri"),
            // Builds before 0.1.10 stored the check moment, played from half a second earlier.
            appStartMs = if (o.has("appStartMs")) o.optLong("appStartMs")
            else (o.optLong("checkAtMs") - 500).coerceAtLeast(0),
        )
    }
}

/** Which cell of which cut is being edited. */
data class Cell(val cut: Int, val top: Boolean)

/**
 * The reel being edited. [pool] is every video picked for it, shown in the tray under the editor;
 * [outro] adds the closing "Link in bio" card (off by default, reels loop better without it).
 */
data class Project(
    val hook: String,
    val cuts: List<Cut>,
    val outro: Boolean = false,
    val pool: List<Uri> = emptyList(),
) {
    companion object {
        private const val PREFS = "project"
        private const val KEY = "current"

        /** The work in progress survives closing the app. */
        fun load(context: Context, defaultHook: String): Project {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return Project(defaultHook, emptyList())
            return runCatching {
                val o = JSONObject(raw)
                val cuts = o.getJSONArray("cuts").let { a -> (0 until a.length()).map { Cut.fromJson(a.getJSONObject(it)) } }
                val pool = o.optJSONArray("pool")?.let { a -> (0 until a.length()).map { Uri.parse(a.getString(it)) } }
                    ?: cuts.flatMap { listOfNotNull(it.topUri, it.appUri) }.distinct()
                Project(o.optString("hook", defaultHook), cuts, o.optBoolean("outro", false), pool)
            }.getOrDefault(Project(defaultHook, emptyList()))
        }

        fun save(context: Context, p: Project) {
            val raw = JSONObject()
                .put("hook", p.hook)
                .put("cuts", JSONArray().apply { p.cuts.forEach { put(it.toJson()) } })
                .put("outro", p.outro)
                .put("pool", JSONArray().apply { p.pool.forEach { put(it.toString()) } })
                .toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
        }
    }
}

/** Hook lines the user saved, newest first, so a good one can be reused on the next reel. */
object Hooks {
    private const val PREFS = "hooks"
    private const val KEY = "saved"

    fun load(context: Context, starter: String): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return listOf(starter)
        return runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }
            .getOrDefault(listOf(starter))
    }

    fun save(context: Context, hooks: List<String>) {
        val raw = JSONArray().apply { hooks.forEach { put(it) } }.toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }
}

private fun JSONObject.uri(key: String): Uri? =
    optString(key).takeIf { it.isNotEmpty() && it != "null" }?.let(Uri::parse)
