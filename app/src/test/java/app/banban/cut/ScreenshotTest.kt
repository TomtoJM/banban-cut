package app.banban.cut

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the app's screens to PNGs on CI, so each release can be looked at on a computer
 * before installing it. Files land in app/build/screens/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h1400dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val template = Template.parse(File("src/main/assets/template.json").readText())

    private val sample = Project(
        hook = template.hookText,
        cuts = listOf(
            Cut(topUri = Uri.parse("content://sample/1"), caption = "7:45am", appUri = Uri.parse("content://sample/2")),
            Cut(topUri = Uri.parse("content://sample/3"), caption = "8:30am", appUri = Uri.parse("content://sample/4")),
            Cut(topUri = Uri.parse("content://sample/5"), caption = "12:10pm"),
        ),
    )

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3182F6))) { Surface(content = content) }
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/screens/$name.png")

    @Test fun home() {
        compose.setContent {
            Themed { ProjectScreen(template, sample, UpdateState.Latest, {}, {}, {}, {}, {}, {}, {}, {}) }
        }
        shoot("1-home")
    }

    @Test fun homeEmpty() {
        compose.setContent {
            val update = UpdateState.Available(Update(BuildConfig.VERSION_CODE + 1, "", ""))
            Themed { ProjectScreen(template, Project(template.hookText, emptyList()), update, {}, {}, {}, {}, {}, {}, {}, {}) }
        }
        shoot("2-empty-with-update")
    }

    @Test fun cutEditor() {
        compose.setContent {
            Themed { CutEditor(template, 1, Cut(caption = "7:45am"), {}, {}) }
        }
        shoot("3-cut-editor")
    }
}
