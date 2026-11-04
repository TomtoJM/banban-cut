package app.banban.cut

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * The check videos Atomic ToDoList saves when a todo is ticked: 1080×675 MP4s (exactly one
 * banban-cut spot) in DCIM/Atomic ToDoList/, oldest first is the order they were ticked, and
 * each one's MediaStore TITLE is the todo's name. See docs/CHECK_CLIPS.md for the agreement.
 */
data class CheckClip(val uri: Uri, val title: String, val durationMs: Long, val key: String)

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
                    // The todo's name; a file name only if the app couldn't set one.
                    val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
                    add(CheckClip(uri, title, ms, key))
                }
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())

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
     * Puts the clips into the bottom spots, one per slide in order: first the slides whose bottom
     * is empty, then new slides. Each clip sits centred at zoom 1, which fills the spot exactly,
     * with the todo's name as its words.
     */
    fun fill(deck: Deck, clips: List<CheckClip>, style: Frame): Deck {
        val slides = deck.slides.toMutableList()
        var i = 0
        for (clip in clips) {
            while (i < slides.size && !(slides[i].layout == Layout.Halves && slides[i].bottom.uri == null)) i++
            if (i >= slides.size) {
                if (slides.size >= Deck.MAX_SLIDES) break
                val blank = style.emptied().copy(text = "")
                slides += Slide(top = blank, bottom = blank)
            }
            val s = slides[i]
            slides[i] = s.copy(bottom = s.bottom.copy(uri = clip.uri, zoom = 1f, x = 0f, y = 0f, durationMs = clip.durationMs, text = clip.title))
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
