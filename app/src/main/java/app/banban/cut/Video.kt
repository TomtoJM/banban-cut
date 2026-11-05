package app.banban.cut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
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

    /** A reel: Instagram's full-screen 9:16. */
    const val REEL_WIDTH = 1080
    const val REEL_HEIGHT = 1920

    /** How long a slide with no video shows in a reel: one check video's length. */
    const val STILL_MS = 2_400L

    /** How long a reel of [deck] runs. */
    fun reelMs(deck: Deck) = deck.slides.sumOf { it.clipMs ?: STILL_MS }

    suspend fun export(context: Context, slide: Slide, face: Typeface, out: File, width: Int = WIDTH, height: Int = HEIGHT) {
        val ms = checkNotNull(slide.clipMs)
        // Everything that reads files happens here, off the main thread.
        val cover = cover(context, slide, face, width, height, overVideo = true)
        // The first sequence is drawn on top: a check video lying over the real-thing spot goes first.
        val videos = slide.frames.withIndex().filter { it.value.isVideo }.sortedBy { if (it.value.check) 0 else 1 }
        val items = videos.map { (part, f) ->
            val size = withContext(Dispatchers.IO) { size(context, checkNotNull(f.uri)) }
            item(slide, part, f, size, ms, alone = videos.size == 1, width, height)
        }
        val composition = Composition.Builder(items.map { EditedMediaItemSequence.Builder(it).build() })
            .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(cover))))))
            // Phones film in HDR more and more; the post and the photos next to it are SDR.
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .apply { if (items.size > 1) setVideoCompositorSettings(placed(videos.map { Art.area(slide, it.index, width.toFloat(), height.toFloat()) }, width, height)) }
            .build()
        run(context, composition, out)
        cover.recycle()
    }

    /**
     * Every slide one after another as one 9:16 video: a photo slide shows for [STILL_MS], a
     * video slide for its video's length. [onProgress] counts slides made ready.
     */
    suspend fun reel(context: Context, deck: Deck, face: Typeface, out: File, onProgress: (Int) -> Unit) {
        val w = REEL_WIDTH
        val h = REEL_HEIGHT
        val temp = mutableListOf<File>()
        val covers = mutableListOf<Bitmap>()
        try {
            val items = deck.slides.mapIndexed { i, slide ->
                val ms = slide.clipMs
                val videos = slide.frames.withIndex().filter { it.value.isVideo }
                val item = when {
                    ms == null -> {
                        // A still: the whole slide as one picture, held.
                        val bmp = cover(context, slide, face, w, h, overVideo = false)
                        val f = File(context.cacheDir, "reel-$i.png").also { temp += it }
                        withContext(Dispatchers.IO) { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                        bmp.recycle()
                        val media = MediaItem.Builder().setUri(android.net.Uri.fromFile(f)).setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(STILL_MS).build()
                        EditedMediaItem.Builder(media).setFrameRate(30).build()
                    }
                    videos.size == 1 -> {
                        // One video: moved into place and covered with the rest of the slide, in one go.
                        val (part, f) = videos[0]
                        val cover = cover(context, slide, face, w, h, overVideo = true).also { covers += it }
                        val size = withContext(Dispatchers.IO) { size(context, checkNotNull(f.uri)) }
                        item(slide, part, f, size, ms, alone = true, w, h, OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(cover))))
                    }
                    else -> {
                        // Two videos need putting together first; that slide is made on its own.
                        val f = File(context.cacheDir, "reel-$i.mp4").also { temp += it }
                        export(context, slide, face, f, w, h)
                        EditedMediaItem.Builder(MediaItem.fromUri(android.net.Uri.fromFile(f))).build()
                    }
                }
                onProgress(i + 1)
                item
            }
            val sequence = EditedMediaItemSequence.Builder(items)
                // Photos and silent videos get silence, so videos with sound keep theirs.
                .experimentalSetForceAudioTrack(true)
                .build()
            val composition = Composition.Builder(sequence)
                .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))))
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()
            run(context, composition, out)
        } finally {
            temp.forEach { it.delete() }
            covers.forEach { it.recycle() }
        }
    }

    private suspend fun cover(context: Context, slide: Slide, face: Typeface, w: Int, h: Int, overVideo: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        withContext(Dispatchers.IO) {
            Art.draw(Canvas(bmp), slide, w.toFloat(), h.toFloat(), face, overVideo = overVideo) { Photos.load(context, it) }
        }
        return bmp
    }

    private suspend fun run(context: Context, composition: Composition, out: File) {
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
    }

    /**
     * One video, cut down to the slide's length and cropped to what shows in its spot. When it's
     * the only video on a two-photo slide, it's also moved into its half of the full frame here,
     * so no compositing is needed.
     */
    private fun item(
        slide: Slide, part: Int, f: Frame, size: android.util.Size, ms: Long, alone: Boolean,
        width: Int, height: Int, cover: Effect? = null,
    ): EditedMediaItem {
        val r = Art.area(slide, part, width.toFloat(), height.toFloat())
        val v = Art.visible(size.width, size.height, f, r)
        val effects = mutableListOf<Effect>(
            Crop(-1 + 2 * v.left, -1 + 2 * v.right, 1 - 2 * v.bottom, 1 - 2 * v.top),
            Presentation.createForWidthAndHeight(r.width().toInt(), r.height().toInt(), Presentation.LAYOUT_STRETCH_TO_FIT),
        )
        if (alone && r.height() < height) {
            // Every spot is full width: fit it into the whole frame (it lands centred), then move it up or down into place.
            effects += Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT)
            val shift = shift(r, height)
            effects += MatrixTransformation { Matrix().apply { postTranslate(0f, shift) } }
        }
        if (cover != null) effects += cover
        val media = MediaItem.Builder()
            .setUri(f.uri)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(ms).build())
            .build()
        return EditedMediaItem.Builder(media).setEffects(Effects(emptyList(), effects)).build()
    }

    /** How far a full-width spot's centre sits from the frame's, in GL units (up is positive). */
    private fun shift(r: android.graphics.RectF, height: Int) = (height / 2f - r.centerY()) / (height / 2f)

    /** Two videos, each moved into its own spot ([areas] in sequence order). */
    private fun placed(areas: List<android.graphics.RectF>, width: Int, height: Int) = object : VideoCompositorSettings {
        override fun getOutputSize(inputSizes: List<Size>) = Size(width, height)
        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings =
            StaticOverlaySettings.Builder()
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, shift(areas[inputId], height))
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
