package app.banban.cut

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** What the editor needs to know about a picked video. */
data class VideoInfo(val durationMs: Long, val width: Int, val height: Int, val takenAt: Long?)

object Media {
    fun info(context: Context, uri: Uri): VideoInfo? = runCatching {
        retrieve(context, uri) { r ->
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            if (rot == 90 || rot == 270) w = h.also { h = w }
            VideoInfo(dur, w, h, parseDate(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)))
        }
    }.getOrNull()

    fun frame(context: Context, uri: Uri, atMs: Long, maxWidth: Int = 540): Bitmap? = runCatching {
        retrieve(context, uri) { r ->
            val f = r.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
            if (f.width <= maxWidth) f
            else Bitmap.createScaledBitmap(f, maxWidth, f.height * maxWidth / f.width, true)
        }
    }.getOrNull()

    // MediaMetadataRetriever is only AutoCloseable from API 29.
    private inline fun <T> retrieve(context: Context, uri: Uri, block: (MediaMetadataRetriever) -> T): T {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            return block(r)
        } finally {
            r.release()
        }
    }

    /** Recorded date looks like 20261022T074512.000Z (UTC). */
    private fun parseDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return runCatching { fmt.parse(raw.take(15))?.time }.getOrNull()?.takeIf { it > 0 }
    }

    /** "7:45am", the caption style from the reference reel. */
    fun clock(epochMs: Long): String =
        SimpleDateFormat("h:mma", Locale.US).format(epochMs).lowercase(Locale.US)

    /**
     * Share of the recording's height that fits in the bottom half when the recording fills its width.
     * A 1080x2400 recording shown in a 1440x1280 half: 1080 * 1280 / 1440 / 2400 = 0.4.
     */
    fun windowFraction(info: VideoInfo, t: Template): Float {
        if (info.width == 0 || info.height == 0) return 0.4f
        val f = info.width.toFloat() * t.halfHeight / t.width / info.height
        return f.coerceIn(0.05f, 1f)
    }
}
