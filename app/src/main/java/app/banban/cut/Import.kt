package app.banban.cut

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Sorts a pile of picked videos. Screen recordings (by name, or by the tall shape of a phone
 * screen) belong in bottom cells, everything else in top cells; each side is put in shooting order
 * and paired up one to one to make a first draft of the cuts.
 */
object Import {
    private class Picked(val uri: Uri, val info: VideoInfo?, val order: Int, val recording: Boolean)

    /** Keeps read access to the picked videos and returns them in the order they should sit in the tray. */
    fun keep(context: Context, uris: List<Uri>): List<Uri> {
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        return uris
    }

    fun isRecording(context: Context, uri: Uri): Boolean {
        val name = displayName(context, uri)?.lowercase() ?: ""
        if ("screen" in name || "record" in name || "화면" in name) return true
        val info = Media.info(context, uri) ?: return false
        if (info.width == 0) return false
        // Camera clips are 16:9 (1.78); a phone screen like the S24+ is about 19.5:9 (2.17).
        return info.height.toFloat() / info.width >= 2.0f
    }

    fun draft(context: Context, uris: List<Uri>): List<Cut> {
        val picked = uris.mapIndexed { i, uri -> Picked(uri, Media.info(context, uri), i, isRecording(context, uri)) }
        val top = inShootingOrder(picked.filterNot { it.recording })
        val bottom = inShootingOrder(picked.filter { it.recording })
        return (0 until maxOf(top.size, bottom.size)).map { i -> Cut(topUri = top.getOrNull(i)?.uri, appUri = bottom.getOrNull(i)?.uri) }
    }

    /** Recorded time when every clip has one, otherwise the order they were picked in. */
    private fun inShootingOrder(list: List<Picked>): List<Picked> =
        if (list.all { it.info?.takenAt != null }) list.sortedBy { it.info!!.takenAt } else list.sortedBy { it.order }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
