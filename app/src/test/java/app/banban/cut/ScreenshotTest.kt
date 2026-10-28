package app.banban.cut

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the app's screens to PNGs on CI, so each release can be looked at on a computer
 * before installing it. Files land in app/build/screens/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun uri(n: Int) = Uri.parse("content://sample/$n")

    /** Stand-in photos: soft two-colour gradients, so the screenshots show framing and words. */
    @Before fun photos() {
        val colors = listOf(0xFF8C6A4F to 0xFF2E3A46, 0xFF5B7F95 to 0xFF1E2A33, 0xFFB08D57 to 0xFF4A3B2A, 0xFF6E8B5E to 0xFF26331F)
        colors.forEachIndexed { i, (a, b) ->
            val bmp = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawRect(0f, 0f, 1200f, 900f, Paint().apply {
                shader = LinearGradient(0f, 0f, 1200f, 900f, a.toInt(), b.toInt(), Shader.TileMode.CLAMP)
            })
            Photos.put(uri(i + 1), bmp)
        }
        // One stand-in is a video, so its length badge shows in the gallery and on the slide.
        Gallery.noteVideo(uri(3), 12_000)
        Phrases.save(RuntimeEnvironment.getApplication(), listOf("Start small.", "One task a day.", "Link in bio"))
    }

    private val deck = Deck(
        listOf(
            Slide(top = Frame(uri(1), "Start small."), bottom = Frame(uri(2), "One task a day.")),
            Slide(top = Frame(uri(3), "Plan the day.", durationMs = 12_000), bottom = Frame()),
            Slide(layout = Layout.Single, top = Frame(uri(4), "Link in bio", textSize = 0.085f, textY = 0.8f)),
        ),
        photos = (1..4).map(::uri),
    )

    @Composable
    private fun Themed(content: @Composable () -> Unit) = BanbanTheme { Surface(content = content) }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/screens/$name.png")

    @Composable
    private fun Editor(d: Deck = deck, gallery: List<Uri>? = (1..4).map(::uri), part: Int? = null, text: Boolean = false, sheet: Boolean = false) =
        EditorScreen(d, {}, UpdateState.Latest, {}, {}, {}, {}, {}, {}, gallery, part, text, sheet)

    @Test fun editor() {
        compose.setContent { Themed { Editor() } }
        shoot("1-editor")
    }

    @Test fun photoSelected() {
        compose.setContent { Themed { Editor(part = 0) } }
        shoot("2-photo")
    }

    @Test fun words() {
        compose.setContent { Themed { Editor(part = 0, text = true) } }
        shoot("3-words")
    }

    @Test fun review() {
        compose.setContent { Themed { Editor(sheet = true) } }
        shoot("5-review")
    }

    @Test fun firstOpen() {
        compose.setContent { Themed { Editor(d = Deck(listOf(Slide())), gallery = null) } }
        shoot("7-first-open")
    }

    /**
     * A finished carousel, drawn at full size by the same code the app saves with. Needs the
     * stock photos CI downloads into build/sample; skipped without them.
     */
    @Test fun sampleCarousel() {
        val dir = File("build/sample")
        val files = (1..7).map { File(dir, "$it.jpg") }
        assumeTrue(files.all { it.exists() })
        val photos = files.mapIndexed { i, f ->
            Uri.parse("file://sample/${i + 1}").also { Photos.put(it, BitmapFactory.decodeFile(f.path)) }
        }
        val sample = Deck(listOf(
            Slide(top = Frame(photos[0], "Write it down."), bottom = Frame(photos[1], "Then do it.")),
            Slide(top = Frame(photos[2], "Start small."), bottom = Frame(photos[3], "Stay consistent.")),
            Slide(top = Frame(photos[4], "One task."), bottom = Frame(photos[5], "One check.")),
            Slide(layout = Layout.Single, top = Frame(photos[6], "Small wins\nadd up.", dim = 0.4f, textSize = 0.09f)),
        ))
        val face = Art.typeface(RuntimeEnvironment.getApplication())
        File("build/screens").mkdirs()
        sample.slides.forEachIndexed { i, slide ->
            val bmp = Bitmap.createBitmap(Deck.WIDTH, Deck.HEIGHT, Bitmap.Config.ARGB_8888)
            Art.draw(Canvas(bmp), slide, Deck.WIDTH.toFloat(), Deck.HEIGHT.toFloat(), face, photo = Photos::peek)
            File("build/screens/carousel-${i + 1}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun saved() {
        compose.setContent { Themed { DoneScreen(3, {}, {}, {}) } }
        shoot("6-done")
    }
}
