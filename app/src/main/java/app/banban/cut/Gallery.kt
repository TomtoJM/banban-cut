package app.banban.cut

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.content.ContextCompat

/**
 * The phone's own photos, newest first, shown under the slides so they can go straight in. The
 * carousels this app saved are left out, so its own output doesn't crowd the list.
 */
object Gallery {
    enum class Access { All, Some, None }

    /** What to ask for: on Android 14+ the person may share just some photos, and that is fine. */
    val permissions: Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun access(context: Context): Access {
        fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 && has(Manifest.permission.READ_MEDIA_IMAGES) -> Access.All
            Build.VERSION.SDK_INT >= 34 && has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.Some
            Build.VERSION.SDK_INT < 33 && has(Manifest.permission.READ_EXTERNAL_STORAGE) -> Access.All
            else -> Access.None
        }
    }

    /** Call off the main thread. */
    fun load(context: Context, limit: Int = 600): List<Uri> = runCatching {
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val path = MediaStore.Images.Media.RELATIVE_PATH
        context.contentResolver.query(
            base,
            arrayOf(MediaStore.Images.Media._ID),
            "($path IS NULL OR $path NOT LIKE ?)",
            arrayOf("Pictures/BanbanCut%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            buildList { while (size < limit && c.moveToNext()) add(ContentUris.withAppendedId(base, c.getLong(id))) }
        } ?: emptyList()
    }.getOrDefault(emptyList())

    private val thumbs = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun peek(uri: Uri): Bitmap? = thumbs.get(uri.toString()) ?: Photos.peek(uri)

    /** A small square-ish picture for the grid. Call off the main thread. */
    fun thumb(context: Context, uri: Uri): Bitmap? = peek(uri) ?: runCatching {
        context.contentResolver.loadThumbnail(uri, Size(320, 320), null)
    }.getOrNull()?.also { thumbs.put(uri.toString(), it) }
}
