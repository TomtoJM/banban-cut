package app.banban.cut

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/** What a batch import turned into. */
data class ImportResult(val cuts: List<Cut>, val shots: Int, val recordings: Int)

/**
 * Turns a pile of picked videos into cuts. Screen recordings (by name, or by the tall shape of a
 * phone screen) go to the bottom half, everything else to the top; each side is put in shooting
 * order and paired up one to one.
 */
object Import {
    private class Picked(val uri: Uri, val info: VideoInfo?, val order: Int)

    fun cuts(context: Context, uris: List<Uri>, t: Template): ImportResult {
        val leadMs = (t.leadSeconds * 1000).toLong()
        val picked = uris.mapIndexed { i, uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            Picked(uri, Media.info(context, uri), i)
        }
        val (apps, shots) = picked.partition { isScreenRecording(context, it) }
        val top = inShootingOrder(shots)
        val bottom = inShootingOrder(apps)
        val cuts = (0 until maxOf(top.size, bottom.size)).map { i ->
            val s = top.getOrNull(i)
            val a = bottom.getOrNull(i)
            Cut(
                topUri = s?.uri,
                caption = s?.info?.takenAt?.let(Media::clock) ?: "",
                appUri = a?.uri,
                checkAtMs = ((a?.info?.durationMs ?: 0) / 2).coerceAtLeast(leadMs),
            )
        }
        return ImportResult(cuts, top.size, bottom.size)
    }

    /** Recorded time when every clip has one, otherwise the order they were picked in. */
    private fun inShootingOrder(list: List<Picked>): List<Picked> =
        if (list.all { it.info?.takenAt != null }) list.sortedBy { it.info!!.takenAt } else list.sortedBy { it.order }

    private fun isScreenRecording(context: Context, p: Picked): Boolean {
        val name = displayName(context, p.uri)?.lowercase() ?: ""
        if ("screen" in name || "record" in name || "화면" in name) return true
        val info = p.info ?: return false
        if (info.width == 0) return false
        // Camera clips are 16:9 (1.78); a phone screen like the S24+ is about 19.5:9 (2.17).
        return info.height.toFloat() / info.width >= 2.0f
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
