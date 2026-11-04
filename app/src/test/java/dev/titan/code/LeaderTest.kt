package dev.titan.code

import dev.titan.code.input.Leader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN §1 principle 4 and §10: every leader command has one key, and no key has two commands. */
class LeaderTest {
    @Test fun noKeyMapsToTwoCommands() {
        val dupes = Leader.entries.groupBy { it.keyCode }.filterValues { it.size > 1 }
        assertTrue("duplicate keys: $dupes", dupes.isEmpty())
    }

    @Test fun shellCommandsAreTheNonEditingOnes() {
        val shell = Leader.entries.filter { !it.editsText }.map { it.keyLabel }.toSet()
        assertEquals(setOf("R", "E", "S", "T", "P", "H", "I", "O"), shell)
    }
}
