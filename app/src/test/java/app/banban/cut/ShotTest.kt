package app.banban.cut

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShotTest {
    private val check = Frame(Uri.parse("content://x/1"), durationMs = 2_400)

    @Test fun lengthFollowsTheCheckVideo() {
        val d = Deck(listOf(Slide(top = Frame(), bottom = check), Slide(top = Frame(), bottom = check.copy(durationMs = 3_100))))
        assertEquals(2_400L, Shot.lengthFor(d, 0, 0))
        assertEquals(3_100L, Shot.lengthFor(d, 1, 0))
        assertEquals(Video.STILL_MS, Shot.lengthFor(Deck(listOf(Slide())), 0, 0))
    }

    @Test fun shapeOfTheSpot() {
        val d = Deck(listOf(Slide()))
        assertEquals(1440f / 900f, Shot.aspectFor(d, 0, 0), 0.01f) // post: 8:5
        assertEquals(9f / 8f, Shot.aspectFor(d.copy(reel = true), 0, 0), 0.01f) // reel: 9:8
    }
}
