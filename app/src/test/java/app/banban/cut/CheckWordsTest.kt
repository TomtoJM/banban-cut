package app.banban.cut

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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

    @Test fun twoClipsTwoSlides() {
        val d = Checks.fill(Deck(listOf(Slide())), clips, Frame())
        assertEquals(2, d.slides.size)
        d.slides.forEachIndexed { i, s ->
            assertEquals(clips[i].title, s.top.text)
            assertEquals("", s.bottom.text)
            assertTrue(s.bottom.check)
            assertEquals(clips[i].uri, s.bottom.uri)
            assertNull(s.top.uri)
            assertEquals(Checks.ACTION_DIM, s.top.dim)
        }
    }

    @Test fun oldSlidesAreLeftAlone() {
        // Made before: words on the check video. Nothing changes them behind the user's back.
        val old = Slide(top = Frame(Uri.parse("content://p/1")), bottom = Frame(clips[0].uri, "Research 5 companies", durationMs = 2_400))
        val d = Checks.fill(Deck(listOf(old)), clips.take(1), Frame())
        assertEquals("Research 5 companies", d.slides[0].bottom.text)
        assertEquals("", d.slides[0].top.text)
        assertEquals(d.slides[0], d.slides[0].wordsToAction()) // no check flag on old slides
        assertEquals(2, d.slides.size) // the new clip went on a new slide, the new way
        assertEquals("Research 5 companies", d.slides[1].top.text)
    }

    @Test fun swapKeepsWordsOnTheRealThing() {
        val s = Checks.fill(Deck(listOf(Slide())), clips.take(1), Frame()).slides[0]
        // The check video moved to the top, the words left behind on what is now the check spot.
        val swapped = s.copy(top = s.top.copy(uri = s.bottom.uri, durationMs = 2_400, check = true), bottom = s.bottom.copy(uri = null, durationMs = 0, check = false))
            .wordsToAction()
        assertEquals("", swapped.top.text)
        assertEquals("Research 5 companies", swapped.bottom.text)
    }

    @Test fun savedPictureHasWordsOnTopOnly() {
        val photo = Uri.parse("content://p/9")
        Photos.put(photo, Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 120, 80)) })
        val s = Checks.fill(Deck(listOf(Slide())), clips.take(1), Frame()).slides[0].let { it.copy(top = it.top.copy(uri = photo)) }
        val face = Art.typeface(RuntimeEnvironment.getApplication())
        val w = Deck.WIDTH.toFloat(); val h = Deck.HEIGHT.toFloat()
        assertNotNull(Art.words(s, 0, w, h, face))
        assertNull(Art.words(s, 1, w, h, face))
        val bmp = Bitmap.createBitmap(Deck.WIDTH, Deck.HEIGHT, Bitmap.Config.ARGB_8888)
        Art.draw(Canvas(bmp), s, w, h, face, overVideo = true) { Photos.peek(it) }
        fun white(y0: Int, y1: Int) = (y0 until y1 step 4).any { y -> (0 until Deck.WIDTH step 4).any { x -> bmp.getPixel(x, y).let { Color.red(it) > 240 && Color.green(it) > 240 && Color.blue(it) > 240 } } }
        assertTrue("words on the top half", white(0, Deck.HEIGHT / 2))
        assertFalse("nothing written over the check video", white(Deck.HEIGHT / 2, Deck.HEIGHT))
    }

    @Test fun addingAPhotoKeepsTheWords() {
        val photo = Uri.parse("content://sample/1")
        Photos.put(photo, Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888))
        var deck by mutableStateOf(Checks.fill(Deck(listOf(Slide())), clips.take(1), Frame()))
        compose.setContent { BanbanTheme { Surface { EditorScreen(deck, { deck = it }, UpdateState.Latest, {}, {}, {}, {}, {}, {}, listOf(photo), startChecks = emptyList()) } } }
        compose.onNode(hasContentDescription("갤러리 1번째", substring = true)).performClick()
        compose.waitForIdle()
        assertEquals(photo, deck.slides[0].top.uri)
        assertEquals("Research 5 companies", deck.slides[0].top.text)
        assertEquals("", deck.slides[0].bottom.text)
    }
}
