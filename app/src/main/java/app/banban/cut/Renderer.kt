package app.banban.cut

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileOutputStream

/**
 * Builds the reel: sequence 0 is the top half (shots, then the end card's top), sequence 1 the
 * bottom half (app recordings, then the end card's bottom). The compositor stacks them on a
 * template-sized canvas and a bitmap overlay adds the captions.
 */
@OptIn(UnstableApi::class)
class Renderer(private val context: Context, private val t: Template) {
    private val cutUs = (t.cutSeconds * 1_000_000).toLong()
    private val cutMs = cutUs / 1000
    private val leadMs = (t.leadSeconds * 1000).toLong()
    private var transformer: Transformer? = null

    fun build(project: Project): Composition {
        val cuts = project.cuts.filter { it.ready }
        val halfFit = Presentation.createForWidthAndHeight(t.width, t.halfHeight, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        val (endTop, endBottom) = endCardItems()

        val top = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
        val bottom = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
        for (cut in cuts) {
            top.addItem(
                EditedMediaItem.Builder(clip(cut.topUri!!, cut.topStartMs))
                    .setRemoveAudio(true)
                    .setEffects(Effects(emptyList(), listOf(halfFit)))
                    .build()
            )
            val appInfo = Media.info(context, cut.appUri!!)
            val window = if (appInfo != null) Media.windowFraction(appInfo, t) else 0.4f
            // Keep the recording's top part (crop is in NDC, y = 1 is the top edge); the Presentation then fills the half.
            val topPart = Crop(-1f, 1f, 1f - 2f * window, 1f)
            bottom.addItem(
                EditedMediaItem.Builder(clip(cut.appUri!!, (cut.checkAtMs - leadMs).coerceAtLeast(0)))
                    .setRemoveAudio(true)
                    .setEffects(Effects(emptyList(), listOf(topPart, halfFit)))
                    .build()
            )
        }
        top.addItem(endTop)
        bottom.addItem(endBottom)

        val captions = CaptionOverlay(cuts.map { it.caption }, project.hook)
        return Composition.Builder(listOf(top.build(), bottom.build()))
            .setVideoCompositorSettings(StackedHalves(t))
            .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(captions)))))
            .build()
    }

    private fun clip(uri: Uri, startMs: Long): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(startMs + cutMs)
                .build()
        )
        .build()

    private fun endCardItems(): Pair<EditedMediaItem, EditedMediaItem> {
        val (a, b) = Overlays.endCard(t)
        fun item(bmp: Bitmap, name: String): EditedMediaItem {
            val f = File(context.cacheDir, name)
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            val mi = MediaItem.Builder()
                .setUri(Uri.fromFile(f))
                .setImageDurationMs((t.endSeconds * 1000).toLong())
                .build()
            return EditedMediaItem.Builder(mi).setFrameRate(t.fps).build()
        }
        return item(a, "end-top.png") to item(b, "end-bottom.png")
    }

    /** Exports to a cache file, then copies it into Movies/BanbanCut. Callbacks run on the main thread. */
    fun export(
        project: Project,
        onProgress: (Int) -> Unit,
        onDone: (Uri) -> Unit,
        onError: (String) -> Unit,
    ) {
        val out = File(context.cacheDir, "banban-${System.currentTimeMillis()}.mp4")
        val main = Handler(Looper.getMainLooper())
        val holder = ProgressHolder()
        val tr = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    val uri = runCatching { saveToGallery(out) }.getOrNull()
                    out.delete()
                    if (uri != null) onDone(uri) else onError("갤러리에 저장하지 못했어요.")
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    transformer = null
                    out.delete()
                    onError("영상을 만들지 못했어요: ${exportException.message}")
                }
            })
            .build()
        transformer = tr
        tr.start(build(project), out.absolutePath)
        main.post(object : Runnable {
            override fun run() {
                val current = transformer ?: return
                if (current.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                main.postDelayed(this, 300)
            }
        })
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }

    private fun saveToGallery(file: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/BanbanCut")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("insert failed")
        resolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** Top half above the seam, bottom half below it, on a template-sized canvas. */
    private class StackedHalves(private val t: Template) : VideoCompositorSettings {
        override fun getOutputSize(inputSizes: List<Size>) = Size(t.width, t.height)

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings =
            StaticOverlaySettings.Builder()
                // Anchors are in NDC: y = 0.5 is the middle of the top half.
                .setBackgroundFrameAnchor(0f, if (inputId == 0) 0.5f else -0.5f)
                .build()
    }

    /** Caption and hook for whichever cut is on screen; nothing over the end card. */
    private inner class CaptionOverlay(private val captions: List<String>, private val hook: String) : BitmapOverlay() {
        private var index = -1
        private var current: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val i = (presentationTimeUs / cutUs).toInt()
            if (i != index) {
                index = i
                current = if (i in captions.indices) Overlays.captions(t, captions[i], hook)
                else Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
            return current
        }
    }
}
