package app.banban.cut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Saves a slide with a video in it as an MP4 that looks just like the slide: each video cropped
 * to its spot the way it's framed in the editor, and the photo, darkening and words drawn by the
 * same [Art] code on a picture laid over the top. 1080×1350 is Instagram's own 4:5 video size.
 */
@OptIn(UnstableApi::class)
object Video {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    suspend fun export(context: Context, slide: Slide, face: Typeface, out: File) {
        val ms = checkNotNull(slide.clipMs)
        // Everything that reads files happens here, off the main thread.
        val cover = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        withContext(Dispatchers.IO) {
            Art.draw(Canvas(cover), slide, WIDTH.toFloat(), HEIGHT.toFloat(), face, overVideo = true) { Photos.load(context, it) }
        }
        val videos = slide.frames.withIndex().filter { it.value.isVideo }
        val items = videos.map { (part, f) ->
            val size = withContext(Dispatchers.IO) { size(context, checkNotNull(f.uri)) }
            item(slide, part, f, size, ms, alone = videos.size == 1)
        }
        val composition = Composition.Builder(items.map { EditedMediaItemSequence.Builder(it).build() })
            .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(cover))))))
            // Phones film in HDR more and more; the post and the photos next to it are SDR.
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .apply { if (items.size > 1) setVideoCompositorSettings(Halves) }
            .build()
        // Transformer runs from a thread with a Looper and calls back on it.
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { done ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.resume(Unit)
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) =
                            done.resumeWithException(exportException)
                    })
                    .build()
                done.invokeOnCancellation { transformer.cancel() }
                transformer.start(composition, out.path)
            }
        }
        cover.recycle()
    }

    /**
     * One video, cut down to the slide's length and cropped to what shows in its spot. When it's
     * the only video on a two-photo slide, it's also moved into its half of the full frame here,
     * so no compositing is needed.
     */
    private fun item(slide: Slide, part: Int, f: Frame, size: android.util.Size, ms: Long, alone: Boolean): EditedMediaItem {
        val r = Art.area(slide, part, WIDTH.toFloat(), HEIGHT.toFloat())
        val v = Art.visible(size.width, size.height, f, r)
        val effects = mutableListOf(
            Crop(-1 + 2 * v.left, -1 + 2 * v.right, 1 - 2 * v.bottom, 1 - 2 * v.top),
            Presentation.createForWidthAndHeight(r.width().toInt(), r.height().toInt(), Presentation.LAYOUT_STRETCH_TO_FIT),
        )
        if (alone && slide.layout == Layout.Halves) {
            effects += Presentation.createForWidthAndHeight(WIDTH, HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT)
            val shift = if (part == 0) 0.5f else -0.5f
            effects += MatrixTransformation { Matrix().apply { postTranslate(0f, shift) } }
        }
        val media = MediaItem.Builder()
            .setUri(f.uri)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(ms).build())
            .build()
        return EditedMediaItem.Builder(media).setEffects(Effects(emptyList(), effects)).build()
    }

    /** Two videos: the first sequence fills the top half, the second the bottom. */
    private val Halves = object : VideoCompositorSettings {
        override fun getOutputSize(inputSizes: List<Size>) = Size(WIDTH, HEIGHT)
        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings =
            StaticOverlaySettings.Builder()
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, if (inputId == 0) 0.5f else -0.5f)
                .build()
    }

    /** The video's size as it is shown, after the phone's rotation. */
    private fun size(context: Context, uri: Uri): android.util.Size {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: WIDTH
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: HEIGHT
            val turn = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            return if (turn % 180 == 0) android.util.Size(w, h) else android.util.Size(h, w)
        } finally { r.release() }
    }
}
