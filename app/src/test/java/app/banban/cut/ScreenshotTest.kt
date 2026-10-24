package app.banban.cut

import android.graphics.Bitmap
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
        Phrases.save(RuntimeEnvironment.getApplication(), listOf("Give charity.", "Tell no one.", "Link in bio"))
    }

    private val deck = Deck(
        listOf(
            Slide(top = Frame(uri(1), "Give charity."), bottom = Frame(uri(2), "Tell no one.")),
            Slide(top = Frame(uri(3), "Plan the day."), bottom = Frame()),
            Slide(layout = Layout.Single, top = Frame(uri(4), "Link in bio", textSize = 0.085f, textY = 0.8f)),
        ),
        photos = (1..4).map(::uri),
    )

    @Composable
    private fun Themed(content: @Composable () -> Unit) = BanbanTheme { Surface(content = content) }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/screens/$name.png")

    @Test fun home() {
        compose.setContent { Themed { HomeScreen(Deck(), UpdateState.Latest, {}, {}, {}, {}) } }
        shoot("1-start")
    }

    @Test fun editor() {
        compose.setContent { Themed { EditorScreen(deck, {}, {}, { _, _ -> }) } }
        shoot("2-editor")
    }

    @Test fun photoSelected() {
        compose.setContent { Themed { EditorScreen(deck, {}, {}, { _, _ -> }, startPart = 1) } }
        shoot("3-photo")
    }

    @Test fun words() {
        compose.setContent { Themed { EditorScreen(deck, {}, {}, { _, _ -> }, startPart = 0, startText = true) } }
        shoot("4-words")
    }

    @Test fun applyWords() {
        compose.setContent { Themed { ApplySheet(deck, 0 to 0, {}, {}) } }
        shoot("5-apply")
    }

    @Test fun saved() {
        compose.setContent { Themed { DoneScreen(3, {}, {}, {}) } }
        shoot("6-saved")
    }
}
