package com.sellernest.poreceiving.data.local.entities

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.memberProperties

/**
 * M3.1: "Room draft schema has no column capable of storing a pre-commit
 * expected quantity" and "A deliberately added leak causes a test to fail."
 * Same technique as `BlindCountDtoTest` (M0.2) applied to the persisted Room
 * entity rather than the network DTO -- both layers need this guarantee
 * independently, since a leak could be introduced in either one.
 */
class DraftLineEntityBlindCountTest {

    @Test
    fun `DraftLineEntity has no property that could represent an expected quantity`() {
        val propertyNames = DraftLineEntity::class.memberProperties.map { it.name.lowercase() }
        val suspiciousNames = propertyNames.filter { it.contains("expected") }

        assertTrue(
            "DraftLineEntity must never carry an expected-quantity column (§6.1, §6.2, M3.1). " +
                "Found suspicious properties: $suspiciousNames",
            suspiciousNames.isEmpty(),
        )
    }
}
