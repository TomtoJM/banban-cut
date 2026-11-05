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

    /**
     * [asset] is the MP4 to copy in; [after] sets TITLE again once the file is published, for
     * the way where the name isn't inside the MP4.
     */
    private fun save(name: String, title: String, asset: String = "check.mp4", after: Boolean = false): Uri {
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
            InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { it.copyTo(out) }
        }
        app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        if (after) {
            Thread.sleep(800)
            app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.TITLE, title) }, null, null)
        }
        Thread.sleep(1_100) // DATE_ADDED is in seconds; one tick per second keeps the order plain.
        return uri
    }

    @After fun clean() {
        made.forEach { runCatching { app.contentResolver.delete(it, null, null) } }
        app.getSharedPreferences("checks", 0).edit().clear().commit()
    }

    @Test fun findsFillsAndRemembers() {
        app.getSharedPreferences("checks", 0).edit().clear().commit()
        // Three ways Atomic ToDoList could pass the todo's name. Only TITLE on insert is known to
        // be lost on Android 15 (the scan sets TITLE from the file); this shows which survive.
        val a = save("check_a.mp4", "Research 5 companies", asset = "check_titled.mp4")
        val b = save("check_b.mp4", "Write the cover letter", after = true)
        val c = save("Go for a run__20261104-101500.mp4", "Go for a run")
        Thread.sleep(3_000) // let any late scan finish
        val all = Checks.find(app)
        println("CHECKS API ${Build.VERSION.SDK_INT} in-mp4=${all[0].title} | set-after=${all[1].title} | insert-only=${all[2].title}")
        assertEquals(listOf(a, b, c), all.map { it.uri })
        assertEquals("name inside the MP4", "Research 5 companies", all[0].title)
        assertEquals("name in the file name", "Go for a run", all[2].title)
        made.remove(c); app.contentResolver.delete(c, null, null)
        val found = Checks.find(app)
        println("CHECKS found: " + found.joinToString { "${it.title} ${it.durationMs}ms ${it.key}" })
        assertEquals(listOf(a, b), found.map { it.uri })
        assertTrue("length ${found[0].durationMs}", found[0].durationMs in 2_000..2_800)

        // One already has a top photo; the second goes on a new slide.
        val deck = Checks.fill(Deck(listOf(Slide(top = Frame(Uri.parse("content://x/1"))))), found, Frame())
        assertEquals(2, deck.slides.size)
        assertEquals(a, deck.slides[0].bottom.uri)
        assertEquals("Research 5 companies", deck.slides[0].top.text)
        assertEquals("", deck.slides[0].bottom.text)
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
