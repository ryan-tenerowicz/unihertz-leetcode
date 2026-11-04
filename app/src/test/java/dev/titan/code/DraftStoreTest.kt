package dev.titan.code

import dev.titan.code.samples.DraftStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class DraftStoreTest {
    @Test fun roundTripAndBackup() {
        val dir = Files.createTempDirectory("drafts").toFile()
        val store = DraftStore(dir)
        assertNull(store.load("two-sum"))
        store.save("two-sum", DraftStore.Draft("a\n        b", 1, 9))
        assertEquals(DraftStore.Draft("a\n        b", 1, 9), store.load("two-sum"))
        store.backup("two-sum", "old")
        assertEquals("old", store.loadBackup("two-sum"))
        assertEquals(listOf("two-sum.backup.py", "two-sum.pos", "two-sum.py"), dir.list()!!.sorted())
    }
}
