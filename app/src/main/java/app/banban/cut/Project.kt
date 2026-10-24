package app.banban.cut

import android.content.Context
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
    val dim: Float = 0.3f,
    val textSize: TextSize = TextSize.Medium,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("uri", uri?.toString()).put("text", text)
        .put("zoom", zoom.toDouble()).put("x", x.toDouble()).put("y", y.toDouble())
        .put("dim", dim.toDouble()).put("textSize", textSize.name)

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) Frame() else Frame(
            uri = o.optString("uri").takeIf { it.isNotEmpty() && it != "null" }?.let(Uri::parse),
            text = o.optString("text"),
            zoom = o.optDouble("zoom", 1.0).toFloat(),
            x = o.optDouble("x", 0.0).toFloat(),
            y = o.optDouble("y", 0.0).toFloat(),
            dim = o.optDouble("dim", 0.3).toFloat(),
            textSize = runCatching { TextSize.valueOf(o.optString("textSize")) }.getOrDefault(TextSize.Medium),
        )
    }
}

/** Share of the slide width the words take, so they look the same at any output size. */
enum class TextSize(val label: String, val ofWidth: Float) { Small("작게", 0.05f), Medium("보통", 0.065f), Large("크게", 0.085f) }

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

    fun toJson(): JSONObject = JSONObject().put("id", id).put("layout", layout.name)
        .put("top", top.toJson()).put("bottom", bottom.toJson())

    companion object {
        fun fromJson(o: JSONObject) = Slide(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            layout = runCatching { Layout.valueOf(o.optString("layout")) }.getOrDefault(Layout.Halves),
            top = Frame.fromJson(o.optJSONObject("top")),
            bottom = Frame.fromJson(o.optJSONObject("bottom")),
        )
    }
}

/** The carousel being made. Instagram takes up to 20 slides in one post. */
data class Deck(val slides: List<Slide> = emptyList()) {
    companion object {
        const val MAX_SLIDES = 20
        /** Instagram's tallest feed shape, 4:5. */
        const val WIDTH = 1080
        const val HEIGHT = 1350
        private const val PREFS = "deck"
        private const val KEY = "current"

        /** Two photos per slide in the order they were picked; an odd last photo gets a slide to itself. */
        fun fromPhotos(uris: List<Uri>): List<Slide> = uris.chunked(2).map { pair ->
            if (pair.size == 2) Slide(top = Frame(pair[0]), bottom = Frame(pair[1]))
            else Slide(layout = Layout.Single, top = Frame(pair[0]))
        }

        fun load(context: Context): Deck {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return Deck()
            return runCatching {
                val a = JSONObject(raw).getJSONArray("slides")
                Deck((0 until a.length()).map { Slide.fromJson(a.getJSONObject(it)) })
            }.getOrDefault(Deck())
        }

        fun save(context: Context, deck: Deck) {
            val raw = JSONObject().put("slides", JSONArray().apply { deck.slides.forEach { put(it.toJson()) } }).toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
        }
    }
}
