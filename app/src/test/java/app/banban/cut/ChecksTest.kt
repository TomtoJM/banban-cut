package app.banban.cut

import org.junit.Assert.assertEquals
import org.junit.Test

class ChecksTest {
    @Test fun names() {
        assertEquals("Research 5 companies", Checks.title("Research 5 companies", "check_a.mp4"))
        assertEquals("Go for a run", Checks.title("Go for a run__20261104-101500", "Go for a run__20261104-101500.mp4"))
        assertEquals("Go for a run", Checks.title(null, "Go for a run__20261104-101500.mp4"))
        assertEquals("check_c", Checks.title("check_c", "check_c.mp4"))
        assertEquals("__1", Checks.title("", "__1.mp4"))
    }
}
