package com.sellernest.poreceiving.data.local

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * §8: "Draft retention: Retained until submitted or explicitly discarded. Never
 * auto-expired." A `fallbackToDestructiveMigration()` call on the database
 * builder would silently wipe every in-progress draft on a schema bump -- the
 * opposite of that guarantee. This scans the actual database wiring source
 * (not a copy) so a future edit that adds the call fails the build immediately.
 */
class NoDestructiveMigrationTest {

    @Test
    fun `database module never falls back to a destructive migration`() {
        val candidates = listOf(
            File("src/main/java/com/sellernest/poreceiving/data/local/DatabaseModule.kt"),
            File("app/src/main/java/com/sellernest/poreceiving/data/local/DatabaseModule.kt"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate DatabaseModule.kt from working dir ${File(".").absolutePath}")

        assertFalse(
            "DatabaseModule.kt must never call fallbackToDestructiveMigration() (§8)",
            file.readText().contains("fallbackToDestructiveMigration"),
        )
    }
}
