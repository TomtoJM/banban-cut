package app.banban.cut

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The todo's name sits on the real-thing spot (top), never on the check video (bottom). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class CheckWordsTest {
    @get:Rule val compose = createComposeRule()

    private val clips = listOf("Research 5 companies", "Go for a run").mapIndexed { i, t ->
        CheckClip(Uri.parse("content://media/external/video/media/${200 + i}"), t, 2_400, "c$i|1")
    }

    private val face get() = Art.typeface(RuntimeEnvironment.getApplication())

    private fun photo(n: Int, color: Int): Uri = Uri.parse("content://p/$n").also {
        Photos.put(it, Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(color) })
    }

    @Test fun twoClipsTwoSlides() {
        val d = Checks.fill(Deck(listOf(Slide())), clips, Frame())
        assertEquals(2, d.slides.size)
        d.slides.forEachIndexed { i, s ->
            assertEquals(clips[i].uri, s.top.uri)
            assertTrue(s.top.check)
            assertEquals("", s.top.text)
            assertEquals(clips[i].title, s.bottom.text)
            assertNull(s.bottom.uri)
            assertEquals(Checks.ACTION_DIM, s.bottom.dim)
        }
    }

    @Test fun oldSlidesAreLeftAlone() {
        // Made by v53: words on the top photo, check video below. Nothing changes them behind the user's back.
        val old = Slide(top = Frame(Uri.parse("content://p/1"), "Research 5 companies"), bottom = Frame(clips[0].uri, durationMs = 2_400, check = true))
        val d = Checks.fill(Deck(listOf(old)), clips.take(1), Frame())
        assertEquals(old, d.slides[0])
        // Its reel layout stays two equal halves too.
        assertEquals(RectF(0f, 960f, 1080f, 1920f), Art.area(old, 1, 1080f, 1920f))
        assertEquals(2, d.slides.size)
        assertEquals(clips[0].uri, d.slides[1].top.uri)
    }

    @Test fun swapKeepsWordsOnTheRealThing() {
        val s = Checks.fill(Deck(listOf(Slide())), clips.take(1), Frame()).slides[0]
        val swapped = s.copy(bottom = s.bottom.copy(uri = s.top.uri, durationMs = 2_400, check = true), top = s.top.copy(uri = null, durationMs = 0, check = false))
            .wordsToAction()
        assertEquals("", swapped.bottom.text)
        assertEquals("Research 5 companies", swapped.top.text)
    }

    @Test fun postSpotsShowTheWholeCheckVideo() {
        val s = Checks.fill(Deck(listOf(Slide())), clips.take(1), Frame()).slides[0]
        val w = Deck.WIDTH.toFloat(); val h = Deck.HEIGHT.toFloat()
        val r = Art.area(s, 0, w, h)
        assertEquals(RectF(0f, 0f, w, h / 2), r)
        assertEquals(RectF(0f, 0f, 1f, 1f), Art.visible(1080, 675, s.top, r)) // nothing cut off
    }

    @Test fun reelCheckSpotIsPinnedToTheTopInsideTheSides() {
        val s = Checks.fill(Deck(listOf(Slide())), clips.take(1), Frame()).slides[0]
        val r = Art.area(s, 0, 1080f, 1920f)
        val side = 1080f * Deck.SAFE_SIDE
        // At the very top, clear of the sides.
        assertEquals(0f, r.top, 0.01f); assertEquals(side, r.left, 0.5f); assertEquals(1080f - side, r.right, 0.5f)
        assertEquals(r.width() * 5f / 8f, r.height(), 0.5f) // its own 8:5 shape
        assertEquals(RectF(0f, 0f, 1f, 1f), Art.visible(1080, 675, s.top, r)) // nothing cut off
        assertEquals(RectF(0f, 0f, 1080f, r.bottom), Art.shown(s, 0, 1080f, 1920f)) // the strip runs edge to edge
        assertEquals(RectF(0f, r.bottom, 1080f, 1920f), Art.shown(s, 1, 1080f, 1920f))
        // An older 9:8 check video: its spot follows its own shape.
        val tall = s.copy(top = s.top.copy(ratio = 8f / 9f))
        assertEquals(r.width() * 8f / 9f, Art.area(tall, 0, 1080f, 1920f).height(), 0.5f)
        // Words sit below the check video and above Instagram's caption.
        val box = Art.words(s, 1, 1080f, 1920f, face)!!.second
        assertTrue("words at ${box.top}", box.top >= r.bottom && box.bottom <= 1920f * (1 - 0.234f))
    }

    private fun white(bmp: Bitmap, y0: Int, y1: Int) = (y0 until y1 step 3).any { y ->
        (0 until bmp.width step 3).any { x -> bmp.getPixel(x, y).let { Color.alpha(it) > 250 && Color.red(it) > 240 && Color.green(it) > 240 && Color.blue(it) > 240 } }
    }

    @Test fun savedPostHasWordsOnTheBottomOnly() {
        val s = Checks.fill(Deck(listOf(Slide())), clips.take(1), Frame()).slides[0].let { it.copy(bottom = it.bottom.copy(uri = photo(9, Color.rgb(90, 120, 80)))) }
        val bmp = Bitmap.createBitmap(Deck.WIDTH, Deck.HEIGHT, Bitmap.Config.ARGB_8888)
        Art.draw(Canvas(bmp), s, Deck.WIDTH.toFloat(), Deck.HEIGHT.toFloat(), face, overVideo = true) { Photos.peek(it) }
        assertFalse("nothing written over the check video", white(bmp, 0, Deck.HEIGHT / 2))
        assertTrue("words on the bottom half", white(bmp, Deck.HEIGHT / 2, Deck.HEIGHT))
        assertEquals("check spot left clear for the video", 0, Color.alpha(bmp.getPixel(540, 300)))
    }

    @Test fun savedReelLeavesTheCheckBandClear() {
        val s = Checks.fill(Deck(listOf(Slide())), clips.take(1), Frame()).slides[0].let { it.copy(bottom = it.bottom.copy(uri = photo(8, Color.rgb(90, 120, 80)))) }
        val bmp = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        Art.draw(Canvas(bmp), s, 1080f, 1920f, face, overVideo = true) { Photos.peek(it) }
        val band = Art.area(s, 0, 1080f, 1920f)
        assertEquals("check video clear, right at the top", 0, Color.alpha(bmp.getPixel(540, 2)))
        assertEquals("check band clear", 0, Color.alpha(bmp.getPixel(540, band.centerY().toInt())))
        assertEquals("sides of the strip filled", 255, Color.alpha(bmp.getPixel(20, band.centerY().toInt())))
        assertEquals("sides of the strip filled", 255, Color.alpha(bmp.getPixel(1060, band.centerY().toInt())))
        assertEquals("photo below", 255, Color.alpha(bmp.getPixel(540, (band.bottom + 100).toInt())))
        assertTrue(white(bmp, band.bottom.toInt(), 1920))
    }

    @Test fun twoClipsTwoPhotos() {
        val a = photo(1, Color.rgb(120, 90, 80)); val b = photo(2, Color.rgb(80, 90, 120))
        var deck by mutableStateOf(Checks.fill(Deck(listOf(Slide())), clips, Frame()))
        compose.setContent { BanbanTheme { Surface { EditorScreen(deck, { deck = it }, UpdateState.Latest, {}, {}, {}, {}, {}, {}, listOf(a, b), startChecks = emptyList()) } } }
        compose.onNode(hasContentDescription("갤러리 1번째", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNode(hasContentDescription("갤러리 2번째", substring = true)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(clips[0].uri, clips[1].uri), deck.slides.map { it.top.uri })
        assertEquals(listOf("", ""), deck.slides.map { it.top.text })
        assertEquals(listOf(a, b), deck.slides.map { it.bottom.uri })
        assertEquals(clips.map { it.title }, deck.slides.map { it.bottom.text })
    }
}
