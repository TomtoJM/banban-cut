package app.banban.cut

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One photo area on a slide: the picture, how it is framed, and the line written over it. */
data class Frame(
    val uri: Uri? = null,
    val text: String = "",
    /** 1 = the photo just covers the area; larger zooms in. */
    val zoom: Float = 1f,
    /** Where the photo sits in its area, -1..1 on each axis (0 = centred). */
    val x: Float = 0f,
    val y: Float = 0f,
    /** How much the photo is darkened so white words stay readable, 0..0.7. */
    val dim: Float = 0f,
    /** Letter height as a share of the slide width, so the words look the same at any output size. */
    val textSize: Float = TEXT_SIZE,
    /** Centre of the words inside this photo's area, 0..1 on each axis. */
    val textX: Float = 0.5f,
    val textY: Float = 0.5f,
    /** How long the clip is when [uri] is a video; 0 for a photo. */
    val durationMs: Long = 0L,
) {
    val isVideo get() = durationMs > 0

    /** The spot with its photo or video taken out; the words stay. */
    fun emptied() = copy(uri = null, zoom = 1f, x = 0f, y = 0f, durationMs = 0L)

    fun toJson(): JSONObject = JSONObject()
        .put("uri", uri?.toString()).put("text", text)
        .put("zoom", zoom.toDouble()).put("x", x.toDouble()).put("y", y.toDouble())
        .put("dim", dim.toDouble()).put("size", textSize.toDouble())
        .put("textX", textX.toDouble()).put("textY", textY.toDouble())
        .put("video", durationMs)

    companion object {
        const val TEXT_SIZE = 0.065f
        val TEXT_SIZES = 0.04f..0.12f

        fun fromJson(o: JSONObject?) = if (o == null) Frame() else Frame(
            uri = o.optString("uri").takeIf { it.isNotEmpty() && it != "null" }?.let(Uri::parse),
            text = o.optString("text"),
            zoom = o.optDouble("zoom", 1.0).toFloat(),
            x = o.optDouble("x", 0.0).toFloat(),
            y = o.optDouble("y", 0.0).toFloat(),
            dim = o.optDouble("dim", 0.0).toFloat(),
            // Earlier versions saved a named size.
            textSize = if (o.has("size")) o.optDouble("size").toFloat()
                else when (o.optString("textSize")) { "Small" -> 0.05f; "Large" -> 0.085f; else -> TEXT_SIZE },
            textX = o.optDouble("textX", 0.5).toFloat(),
            textY = o.optDouble("textY", 0.5).toFloat(),
            durationMs = o.optLong("video", 0L),
        )
    }
}

/** Two photos stacked (the reference look), or one photo filling the slide. */
enum class Layout { Halves, Single }

data class Slide(
    val id: String = UUID.randomUUID().toString(),
    val layout: Layout = Layout.Halves,
    val top: Frame = Frame(),
    val bottom: Frame = Frame(),
) {
    /** The frames this layout shows; a single-photo slide uses only [top]. */
    val frames get() = if (layout == Layout.Halves) listOf(top, bottom) else listOf(top)

    fun with(part: Int, f: Frame) = if (part == 0) copy(top = f) else copy(bottom = f)
    fun frame(part: Int) = if (part == 0) top else bottom

    /**
     * How long this slide plays when it has a video: the shortest clip on it, so neither half
     * runs out early, and at most a minute, Instagram's limit for a carousel video. Null for photos.
     */
    val clipMs: Long? get() = frames.filter { it.isVideo }.minOfOrNull { it.durationMs }?.coerceAtMost(MAX_CLIP_MS)

    fun toJson(): JSONObject = JSONObject().put("id", id).put("layout", layout.name)
        .put("top", top.toJson()).put("bottom", bottom.toJson())

    companion object {
        const val MAX_CLIP_MS = 60_000L

        fun fromJson(o: JSONObject) = Slide(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            layout = runCatching { Layout.valueOf(o.optString("layout")) }.getOrDefault(Layout.Halves),
            top = Frame.fromJson(o.optJSONObject("top")),
            bottom = Frame.fromJson(o.optJSONObject("bottom")),
        )
    }
}

/**
 * The carousel being made, plus every photo picked for it. The photos wait in the tray under the
 * slides, so any of them can be dropped into any spot. Instagram takes up to 20 slides in one post.
 */
data class Deck(val slides: List<Slide> = emptyList(), val photos: List<Uri> = emptyList()) {
    /** How many spots on the slides show this photo. */
    fun uses(uri: Uri) = slides.sumOf { s -> s.frames.count { it.uri == uri } }

    /** The first spot with no photo yet, looking from [from] onwards and then from the start. */
    fun firstEmpty(from: Int = 0): Pair<Int, Int>? {
        val order = slides.indices.drop(from) + slides.indices.take(from)
        for (i in order) slides[i].frames.forEachIndexed { p, f -> if (f.uri == null) return i to p }
        return null
    }

    companion object {
        const val MAX_SLIDES = 20
        const val MAX_PHOTOS = 40

        /**
         * Instagram's tallest carousel shape, 4:5, at 1440 wide. Instagram shows feed photos up
         * to 1440 wide, so this keeps the most detail (our own size guide).
         */
        const val WIDTH = 1440
        const val HEIGHT = 1800

        /**
         * Edges Instagram covers with its own buttons on a 4:5 post (our size guide): the profile
         * row on top, like / share / more at the sides, the caption below. Words stay inside.
         */
        const val SAFE_SIDE = 0.08f
        const val SAFE_TOP = 0.055f
        const val SAFE_BOTTOM = 0.085f

        private const val PREFS = "deck"
        private const val KEY = "current"

        /**
         * Two photos per slide in the order they were picked, and the last photo full on its own
         * slide. If the pairs don't come out even, the last pair keeps an empty spot to fill from the tray.
         */
        fun fromPhotos(uris: List<Uri>, style: Frame = Frame()): List<Slide> {
            if (uris.isEmpty()) return emptyList()
            fun f(u: Uri?) = style.copy(uri = u, text = "")
            val pairs = uris.dropLast(1).chunked(2).map { Slide(top = f(it[0]), bottom = f(it.getOrNull(1))) }
            return pairs + Slide(layout = Layout.Single, top = f(uris.last()))
        }

        fun start(uris: List<Uri>, style: Frame = Frame()) = Deck(fromPhotos(uris, style).takeLast(MAX_SLIDES), uris.take(MAX_PHOTOS))

        /**
         * Photo picker links stop working once the app closes unless we ask to keep them, and
         * "continue where I left off" needs them the next day.
         */
        fun keepAccess(context: Context, uris: List<Uri>) = uris.forEach {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }

        fun load(context: Context): Deck {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return Deck()
            return runCatching {
                val o = JSONObject(raw)
                val a = o.getJSONArray("slides")
                val slides = (0 until a.length()).map { Slide.fromJson(a.getJSONObject(it)) }
                val p = o.optJSONArray("photos")
                val photos = if (p != null) (0 until p.length()).map { Uri.parse(p.getString(it)) }
                    else slides.flatMap { s -> s.frames.mapNotNull { it.uri } }.distinct()
                Deck(slides, photos)
            }.getOrDefault(Deck())
        }

        fun save(context: Context, deck: Deck) {
            val raw = JSONObject()
                .put("slides", JSONArray().apply { deck.slides.forEach { put(it.toJson()) } })
                .put("photos", JSONArray().apply { deck.photos.forEach { put(it.toString()) } })
                .toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
        }
    }
}

/** Lines the user typed and kept, to put on later slides and later carousels. */
object Phrases {
    private const val PREFS = "phrases"
    private const val KEY = "list"

    /** The lines early versions showed as examples; they are not the user's own. */
    private val samples = setOf("Give charity.", "Tell no one.")

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching { JSONArray(raw).let { a -> (0 until a.length()).map(a::getString) } }.getOrDefault(emptyList()) - samples
    }

    fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, JSONArray(list).toString()).apply()
    }
}

/**
 * The look last chosen for words and darkening. Someone posting every day sets the same size and
 * place each time, so new carousels start with it.
 */
object LastStyle {
    private const val PREFS = "style"
    private const val KEY = "frame2"

    fun load(context: Context): Frame =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { Frame.fromJson(org.json.JSONObject(it)) }.getOrNull() } ?: Frame()

    fun save(context: Context, f: Frame) {
        val style = Frame(dim = f.dim, textSize = f.textSize, textX = f.textX, textY = f.textY)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, style.toJson().toString()).apply()
    }
}
