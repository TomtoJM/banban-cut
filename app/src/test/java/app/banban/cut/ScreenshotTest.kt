package app.banban.cut

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val template = Template.parse(File("src/main/assets/template.json").readText())

    private fun uri(n: Int) = Uri.parse("content://sample/$n")

    private val sample = Project(
        hook = template.hookText,
        cuts = listOf(
            Cut(topUri = uri(1), appUri = uri(2)),
            Cut(topUri = uri(3), appUri = uri(4)),
            Cut(topUri = uri(5)),
        ),
        pool = (1..6).map(::uri),
    )

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3182F6))) { Surface(content = content) }
    }

    @Composable
    private fun Editor(project: Project, update: UpdateState = UpdateState.Latest, selected: Cell? = null) = Themed {
        EditorScreen(
            template = template, project = project, hooks = listOf(template.hookText, "POV: you actually did it."),
            update = update, play = PlayState(positionMs = 2100), lengthMs = 7200,
            // The real preview is a video surface; a stand-in shows where it sits.
            preview = { m -> Box(m.background(Color(0xFF2A2F37))) { Text("미리보기", color = Color.White, modifier = Modifier.align(Alignment.Center)) } },
            selected = selected, onSelect = {}, onChange = {}, onHooks = {}, onPlayPause = {}, onSeek = {},
            onCheckUpdate = {}, onInstall = {}, onExportStarted = {}, onOpen = {}, onShare = {},
        )
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/screens/$name.png")

    @Test fun editor() {
        compose.setContent { Editor(sample) }
        shoot("1-editor")
    }

    @Test fun editorCellSelected() {
        compose.setContent { Editor(sample, selected = Cell(2, top = false)) }
        shoot("2-cell-selected")
    }

    @Test fun empty() {
        val update = UpdateState.Available(Update(BuildConfig.VERSION_CODE + 1, "", ""))
        compose.setContent { Editor(Project(template.hookText, emptyList()), update) }
        shoot("3-start")
    }
}
