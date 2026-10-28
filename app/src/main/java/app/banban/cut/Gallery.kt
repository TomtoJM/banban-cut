package app.banban.cut

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * The phone's own photos and videos, newest first, shown under the slides so they can go straight
 * in. The carousels this app saved are left out, so its own output doesn't crowd the list.
 */
object Gallery {
    /** [PhotosOnly]: an earlier version asked for photos only, so videos still need a yes. */
    enum class Access { All, Some, PhotosOnly, None }

    /** What to ask for: on Android 14+ the person may share just some, and that is fine. */
    val permissions: Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun access(context: Context): Access {
        fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT < 33 -> if (has(Manifest.permission.READ_EXTERNAL_STORAGE)) Access.All else Access.None
            has(Manifest.permission.READ_MEDIA_IMAGES) && has(Manifest.permission.READ_MEDIA_VIDEO) -> Access.All
            has(Manifest.permission.READ_MEDIA_IMAGES) -> Access.PhotosOnly
            Build.VERSION.SDK_INT >= 34 && has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.Some
            else -> Access.None
        }
    }

    /** Video lengths by uri, filled in as the grid loads; photos aren't in it. */
    private val lengths = ConcurrentHashMap<String, Long>()

    fun isVideo(uri: Uri) = lengths.containsKey(uri.toString())

    /** Tells the gallery a uri is a video of [ms], for a video that didn't come through [load]. */
    fun noteVideo(uri: Uri, ms: Long) { lengths[uri.toString()] = ms.coerceAtLeast(1L) }

    /** How long a video is, or 0 for a photo. Asks the file itself if the grid didn't say. */
    fun durationMs(context: Context, uri: Uri): Long = lengths[uri.toString()] ?: run {
        if (context.contentResolver.getType(uri)?.startsWith("video/") != true) return 0L
        val ms = runCatching {
            MediaMetadataRetriever().run {
                try { setDataSource(context, uri); extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() } finally { release() }
            }
        }.getOrNull() ?: 0L
        ms.coerceAtLeast(1L).also { lengths[uri.toString()] = it }
    }

    /** Call off the main thread. */
    fun load(context: Context, limit: Int = 600): List<Uri> = runCatching {
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val type = MediaStore.Files.FileColumns.MEDIA_TYPE
        val path = MediaStore.MediaColumns.RELATIVE_PATH
        context.contentResolver.query(
            files,
            arrayOf(MediaStore.MediaColumns._ID, type, MediaStore.MediaColumns.DURATION),
            "$type IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})" +
                " AND ($path IS NULL OR ($path NOT LIKE ? AND $path NOT LIKE ?))",
            arrayOf("Pictures/BanbanCut%", "Movies/BanbanCut%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val kind = c.getColumnIndexOrThrow(type)
            val length = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            buildList {
                while (size < limit && c.moveToNext()) {
                    if (c.getInt(kind) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(id))
                        lengths[uri.toString()] = c.getLong(length).coerceAtLeast(1L)
                        add(uri)
                    } else add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(id)))
                }
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())

    private val thumbs = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun peek(uri: Uri): Bitmap? = thumbs.get(uri.toString()) ?: Photos.peek(uri)

    /** A small square-ish picture for the grid; for a video, its opening frame. Call off the main thread. */
    fun thumb(context: Context, uri: Uri): Bitmap? = peek(uri) ?: runCatching {
        context.contentResolver.loadThumbnail(uri, Size(320, 320), null)
    }.getOrNull()?.also { thumbs.put(uri.toString(), it) }
}

/** "0:07", "1:30": a video's length the way the Photos app shows it. */
fun clock(ms: Long): String {
    val s = (ms + 500) / 1000
    return "${s / 60}:${"%02d".format(s % 60)}"
}
