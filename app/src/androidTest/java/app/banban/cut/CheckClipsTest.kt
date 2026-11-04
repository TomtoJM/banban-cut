package app.banban.cut

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plays Atomic ToDoList's part on a real Android: saves check videos into DCIM/Atomic ToDoList/
 * with the todo as TITLE, the way docs/CHECK_CLIPS.md says, then checks that banban-cut finds
 * them in the order ticked, with the todo's name surviving the phone's media scan, puts them in
 * the bottom spots, never offers a used one again, and can ask to send them to the trash.
 */
@RunWith(AndroidJUnit4::class)
class CheckClipsTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val made = mutableListOf<Uri>()

    private fun save(name: String, title: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Checks.FOLDER)
            put(MediaStore.MediaColumns.TITLE, title)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(app.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
        made += uri
        app.contentResolver.openOutputStream(uri)!!.use { out ->
            InstrumentationRegistry.getInstrumentation().context.assets.open("check.mp4").use { it.copyTo(out) }
        }
        app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        Thread.sleep(1_100) // DATE_ADDED is in seconds; one tick per second keeps the order plain.
        return uri
    }

    @After fun clean() {
        made.forEach { runCatching { app.contentResolver.delete(it, null, null) } }
        app.getSharedPreferences("checks", 0).edit().clear().commit()
    }

    @Test fun findsFillsAndRemembers() {
        app.getSharedPreferences("checks", 0).edit().clear().commit()
        val a = save("check_a.mp4", "Research 5 companies")
        val b = save("check_b.mp4", "Write the cover letter")
        Thread.sleep(1_500) // let the scan finish
        val found = Checks.find(app)
        println("CHECKS found: " + found.joinToString { "${it.title} ${it.durationMs}ms ${it.key}" })
        assertEquals(listOf(a, b), found.map { it.uri })
        assertEquals(listOf("Research 5 companies", "Write the cover letter"), found.map { it.title })
        assertTrue("length ${found[0].durationMs}", found[0].durationMs in 2_000..2_800)

        // One already has a top photo; the second goes on a new slide.
        val deck = Checks.fill(Deck(listOf(Slide(top = Frame(Uri.parse("content://x/1"))))), found, Frame())
        assertEquals(2, deck.slides.size)
        assertEquals(a, deck.slides[0].bottom.uri)
        assertEquals("Research 5 companies", deck.slides[0].bottom.text)
        assertEquals(1f, deck.slides[0].bottom.zoom)
        assertEquals(b, deck.slides[1].bottom.uri)
        assertEquals(listOf(a, b), deck.originals())

        // Saved once, never offered again, even if it comes back from the trash.
        Checks.markUsed(app, listOf(a))
        assertEquals(listOf(b), Checks.find(app).map { it.uri })

        if (Build.VERSION.SDK_INT >= 30) {
            val request = MediaStore.createTrashRequest(app.contentResolver, listOf(a, b), true)
            assertNotNull(request.intentSender)
            println("CHECKS trash request ready on API ${Build.VERSION.SDK_INT}")
        }
    }
}
