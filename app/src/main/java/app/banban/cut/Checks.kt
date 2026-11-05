package app.banban.cut

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * The check videos Atomic ToDoList saves when a todo is ticked: 1080×675 MP4s (exactly one
 * banban-cut spot) in DCIM/Atomic ToDoList/, oldest first is the order they were ticked, and
 * each one's MediaStore TITLE (from the title inside the MP4) or file name is the todo's name. See docs/CHECK_CLIPS.md for the agreement.
 */
data class CheckClip(val uri: Uri, val title: String, val durationMs: Long, val key: String, val ratio: Float = Frame.CHECK_RATIO)

object Checks {
    const val FOLDER = "DCIM/Atomic ToDoList/"

    private const val PREFS = "checks"
    private const val USED = "used"

    /** Check videos not used in a saved carousel yet, in the order they were ticked. Call off the main thread. */
    fun find(context: Context, skip: Set<Uri> = emptySet()): List<CheckClip> = runCatching {
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val used = used(context)
        context.contentResolver.query(
            base,
            arrayOf(
                MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.ORIENTATION,
            ),
            "${MediaStore.Video.Media.RELATIVE_PATH} = ?",
            arrayOf(FOLDER),
            "${MediaStore.Video.Media.DATE_ADDED} ASC, ${MediaStore.Video.Media._ID} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val uri = ContentUris.withAppendedId(base, c.getLong(0))
                    val name = c.getString(2).orEmpty()
                    val key = "$name|${c.getLong(3)}"
                    if (key in used || uri in skip) continue
                    val ms = c.getLong(4).coerceAtLeast(1L)
                    Gallery.noteVideo(uri, ms)
                    // The video's shape as shown, so its spot can show all of it.
                    val vw = c.getInt(5); val vh = c.getInt(6)
                    val turned = c.getInt(7) % 180 != 0
                    val ratio = if (vw > 0 && vh > 0) (if (turned) vw.toFloat() / vh else vh.toFloat() / vw) else Frame.CHECK_RATIO
                    add(CheckClip(uri, title(c.getString(1), name), ms, key, ratio))
                }
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())

    /**
     * The todo's name. Android fills TITLE from the title inside the MP4 when it scans the file,
     * and from the file name when there isn't one (a TITLE set by the saving app is lost on
     * Android 15). So: TITLE when it's a real name, else the file name up to "__", which is how
     * Atomic ToDoList names files as a fallback ("Research 5 companies__20261104-101500.mp4").
     */
    fun title(title: String?, displayName: String): String {
        val stem = displayName.substringBeforeLast('.')
        return title?.trim()?.takeIf { it.isNotEmpty() && it != stem } ?: stem.substringBefore("__").trim().ifEmpty { stem }
    }

    /**
     * Remembers check videos that went into a saved carousel, by file name and size, so they
     * aren't offered again even if they come back out of the trash.
     */
    fun markUsed(context: Context, uris: Collection<Uri>) {
        if (uris.isEmpty()) return
        val keys = runCatching {
            uris.mapNotNull { uri ->
                context.contentResolver.query(
                    uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.RELATIVE_PATH), null, null, null,
                )?.use { c -> if (c.moveToFirst() && c.getString(2) == FOLDER) "${c.getString(0)}|${c.getLong(1)}" else null }
            }
        }.getOrDefault(emptyList())
        if (keys.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(USED, used(context) + keys).apply()
    }

    private fun used(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(USED, emptySet()).orEmpty().toSet()

    /**
     * Puts the clips into the top spots, one per slide in order: first the slides whose top is
     * empty, then new slides. Each clip sits centred at zoom 1, which shows all of it, with no
     * words; the todo's name goes on the same slide's bottom spot (unless it has words).
     */
    /** How much an action spot is darkened at first, so white words read over a bright photo. */
    const val ACTION_DIM = 0.25f

    fun fill(deck: Deck, clips: List<CheckClip>, style: Frame): Deck {
        val slides = deck.slides.toMutableList()
        var i = 0
        for (clip in clips) {
            while (i < slides.size && !(slides[i].layout == Layout.Halves && slides[i].top.uri == null)) i++
            if (i >= slides.size) {
                if (slides.size >= Deck.MAX_SLIDES) break
                val blank = style.emptied().copy(text = "")
                slides += Slide(top = blank, bottom = blank)
            }
            val s = slides[i]
            // The check video goes on top, wordless, at zoom 1 (its whole width shows). The todo's
            // name goes on the bottom spot, where the real thing will be shown, with a little
            // darkening so the words read over any photo.
            val app = s.top.copy(uri = clip.uri, zoom = 1f, x = 0f, y = 0f, durationMs = clip.durationMs, text = "", dim = 0f, check = true, ratio = clip.ratio)
            val act = if (s.bottom.text.isNotBlank()) s.bottom else s.bottom.copy(text = clip.title, dim = maxOf(s.bottom.dim, ACTION_DIM))
            slides[i] = s.copy(top = app, bottom = act)
            i++
        }
        return deck.copy(slides = slides)
    }
}

/**
 * The photos and videos from the phone's own library that a carousel uses, the ones that can go
 * to the trash once it's saved. Photo-picker links and files are left out: they aren't ours to trash.
 */
fun Deck.originals(): List<Uri> = slides.flatMap { s -> s.frames.mapNotNull { it.uri } }.distinct()
    .filter { it.authority == MediaStore.AUTHORITY && it.pathSegments.firstOrNull()?.startsWith("external") == true }
