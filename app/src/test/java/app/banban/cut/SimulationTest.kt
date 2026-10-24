package app.banban.cut

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Makes three 5-photo posts from start to finish the way a person would, tap by tap, and writes
 * what happened to build/screens/sim.txt: every tap, whether it did what was expected, and how
 * many taps a post took. Pictures of the key moments land next to it as sim-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class SimulationTest {
    @get:Rule val compose = createComposeRule()

    private val gallery = (1..12).map { Uri.parse("content://sim/$it") }
    private val log = StringBuilder()
    private var taps = 0
    private var deck by mutableStateOf(Deck())

    @Before fun photos() {
        gallery.forEachIndexed { i, uri ->
            val bmp = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
            val hue = i * 30f
            val a = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.35f, 0.7f))
            val b = android.graphics.Color.HSVToColor(floatArrayOf(hue + 20f, 0.5f, 0.3f))
            Canvas(bmp).drawRect(0f, 0f, 900f, 1200f, Paint().apply { shader = LinearGradient(0f, 0f, 900f, 1200f, a, b, Shader.TileMode.CLAMP) })
            Photos.put(uri, bmp)
        }
    }

    private fun blank() = Deck(listOf(Slide()))
    private fun note(s: String) { log.appendLine(s); println("SIM " + s) }
    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/screens/sim-$name.png")
    private fun state() = deck.slides.mapIndexed { i, s ->
        "${i + 1}:" + s.frames.joinToString("/") { f -> (f.uri?.lastPathSegment?.let { "사진$it" } ?: "빈칸") + (if (f.text.isNotEmpty()) "[${f.text}]" else "") }
    }.joinToString("  ")

    private fun tapPhoto(n: Int) {
        taps++
        val before = deck.uses(gallery[n - 1])
        compose.onNode(hasContentDescription("갤러리 ${n}번째 사진", substring = true)).performClick()
        compose.waitForIdle()
        val after = deck.uses(gallery[n - 1])
        val ok = if (before == 0) after > 0 else after == 0
        note("  탭: 갤러리 사진$n → ${if (ok) "OK" else "기대와 다름"} | ${state()}")
    }

    private fun tapText(t: String): Boolean = runCatching {
        taps++
        compose.onNode(hasText(t) and hasClickAction()).performClick(); compose.waitForIdle(); true
    }.getOrElse { note("  탭: '$t' 버튼을 찾지 못함"); false }

    /** Taps a spot on the current slide: 0 = top photo, 1 = bottom photo. */
    private fun tapSpot(slide: Int, part: Int) {
        taps++
        compose.onNode(hasContentDescription("슬라이드 $slide")).performTouchInput {
            click(Offset(width * 0.25f, height * (if (part == 0) 0.12f else 0.62f)))
        }
        compose.waitForIdle()
    }

    private fun type(words: String) {
        taps++
        compose.onNode(hasSetTextAction()).performTextReplacement(words)
        compose.waitForIdle()
    }

    private fun swipeTo(slide: Int) {
        // People swipe; the dots move with it. Tapping the peeking neighbour does the same.
        taps++
        compose.onNode(hasContentDescription("슬라이드 $slide")).performTouchInput { click(Offset(width * 0.04f, height * 0.5f)) }
        compose.waitForIdle()
    }

    private val lines = listOf(
        listOf("Write it down.", "Then do it.", "Start small.", "Stay with it.", "Small wins add up."),
        listOf("Monday.", "One task.", "One check.", "Again tomorrow.", "Link in bio"),
        listOf("Too many tabs?", "Pick one.", "Finish it.", "Check it off.", "That's the day."),
    )

    @Test fun threePosts() {
        compose.setContent { BanbanTheme { Surface { EditorScreen(deck, { deck = it }, UpdateState.Latest, {}, {}, {}, {}, {}, { deck = blank() }, gallery) } } }
        repeat(3) { r ->
            deck = blank(); taps = 0
            compose.waitForIdle()
            note("== 게시물 ${r + 1} (사진 5장) ==")
            // 1. Put five photos in, one tap each, as fast as people do.
            val picks = (1..5).map { it + r * 3 }
            picks.forEach(::tapPhoto)
            shot("r${r + 1}-1-photos")
            val filled = deck.slides.sumOf { s -> s.frames.count { it.uri != null } }
            note("  → 사진 ${filled}장 들어감 (원한 5장)")
            // A slip: tap a photo that's already in, then put it back.
            if (r == 1) { tapPhoto(picks[1]); tapPhoto(picks[1]) }
            // 2. Words on every photo.
            var k = 0
            deck.slides.forEachIndexed { i, s ->
                if (i > 0) swipeTo(i + 1)
                s.frames.indices.forEach { p ->
                    if (deck.slides[i].frame(p).uri == null) return@forEach
                    tapSpot(i + 1, p)
                    if (!tapText("문구")) return@forEach
                    type(lines[r][k++.coerceAtMost(4)])
                    tapText("완료")
                }
            }
            shot("r${r + 1}-2-words")
            note("  문구 후: ${state()}")
            // 3. Save, look, download.
            tapText("저장")
            shot("r${r + 1}-3-review")
            if (compose.onAllNodes(hasText("갤러리에 내려받기", substring = true)).fetchSemanticsNodes().isEmpty()) {
                note("  저장이 회색이라 안 눌림 → 고르던 사진을 다시 눌러 선택을 풀고 다시 저장")
                deck.slides.last().frames.indices.last().let { tapSpot(deck.slides.size, it) }
                tapText("저장")
            }
            val reviewing = compose.onAllNodes(hasText("갤러리에 내려받기", substring = true)).fetchSemanticsNodes().isNotEmpty()
            note("  저장 → 미리보기 ${if (reviewing) "열림" else "안 열림"}")
            tapText("계속 고치기")
            note("  게시물 ${r + 1}: 총 ${taps}번 누름")
        }
        File("build/screens").mkdirs()
        File("build/screens/sim.txt").writeText(log.toString())
    }
}
