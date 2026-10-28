package app.banban.cut

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Saves real slides with videos on a device (CI runs it on an emulator) and checks each MP4: its
 * size, its length, and a picture of one frame, kept in files/videocheck/ to look at.
 * The clips are made on CI with ffmpeg: land.mp4 is 1280×720 with sound, port.mp4 is 720×1280.
 */
@RunWith(AndroidJUnit4::class)
class VideoExportTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val out = File(app.filesDir, "videocheck").apply { mkdirs() }

    private fun clip(name: String, ms: Long): Uri {
        val f = File(app.cacheDir, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { i -> f.outputStream().use { i.copyTo(it) } }
        return Uri.fromFile(f).also { Gallery.noteVideo(it, ms) }
    }

    private fun photo(): Uri {
        val bmp = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(Color.rgb(0x6E, 0x8B, 0x5E))
            drawCircle(600f, 450f, 300f, Paint().apply { color = Color.rgb(0xF2, 0xC1, 0x4E) })
        }
        val f = File(app.cacheDir, "photo.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(f)
    }

    private fun check(name: String, slide: Slide, expectMs: Long) = runBlocking {
        val file = File(out, "$name.mp4")
        Video.export(app, slide, Art.typeface(app), file)
        assertTrue("$name: nothing written", file.length() > 10_000)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.path)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
            val turn = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            val shown = if (turn % 180 == 0) w to h else h to w
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            val audio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            println("VIDEOCHECK $name: ${shown.first}x${shown.second} ${ms}ms audio=$audio ${file.length() / 1024}KB")
            assertEquals("$name: size", Video.WIDTH to Video.HEIGHT, shown)
            assertTrue("$name: length $ms, wanted about $expectMs", kotlin.math.abs(ms - expectMs) < 700)
            r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST)!!.let { frame ->
                File(out, "$name.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { r.release() }
    }

    @Test fun videoOverPhoto() {
        val v = clip("land.mp4", 5000)
        check("1-video-photo", Slide(top = Frame(v, "Write it down.", durationMs = 5000), bottom = Frame(photo(), "Then do it.")), 5000)
    }

    @Test fun photoOverVideo() {
        val v = clip("port.mp4", 4000)
        check("2-photo-video", Slide(top = Frame(photo(), "Start small."), bottom = Frame(v, "One task a day.", dim = 0.3f, durationMs = 4000)), 4000)
    }

    @Test fun twoVideos() {
        val a = clip("land.mp4", 5000)
        val b = clip("port.mp4", 4000)
        // The shorter clip sets the length.
        check("3-two-videos", Slide(top = Frame(a, "Monday.", durationMs = 5000), bottom = Frame(b, "One check.", durationMs = 4000)), 4000)
    }

    @Test fun fullVideoZoomed() {
        val v = clip("port.mp4", 4000)
        check("4-full-video", Slide(layout = Layout.Single, top = Frame(v, "Small wins\nadd up.", zoom = 1.5f, x = 0.5f, textY = 0.8f, durationMs = 4000)), 4000)
    }
}
