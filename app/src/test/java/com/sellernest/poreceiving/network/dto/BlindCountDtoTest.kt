package com.sellernest.poreceiving.network.dto

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.memberProperties

/**
 * §6.2: "the backend omits quantity_expected from the payload entirely... The
 * field is absent, not null, not zero." §6.1/§11: no expected quantity may be
 * displayed or *held on device* before commit, verified by inspecting the
 * network payload, not merely the UI.
 *
 * This asserts that guarantee at the type level: [PurchaseOrderLine] — the only
 * pre-commit line model — has no property whose name could carry an expected
 * quantity, under any of the spellings a future edit might introduce. If someone
 * adds `quantityExpected`, `expectedQuantity`, or similar to this class, this test
 * fails the build immediately, before it ever reaches a screen.
 */
class BlindCountDtoTest {

    @Test
    fun `PurchaseOrderLine has no property that could represent an expected quantity`() {
        val propertyNames = PurchaseOrderLine::class.memberProperties.map { it.name.lowercase() }
        val suspiciousNames = propertyNames.filter { it.contains("expected") }

        assertTrue(
            "PurchaseOrderLine must never carry an expected-quantity field before commit " +
                "(§6.1, §6.2). Found suspicious properties: $suspiciousNames",
            suspiciousNames.isEmpty(),
        )
    }

    @Test
    fun `PurchaseOrderDetail's line list type is exactly the blind-safe PurchaseOrderLine`() {
        val linesProperty = PurchaseOrderDetail::class.memberProperties.first { it.name == "lines" }
        val returnTypeArg = linesProperty.returnType.arguments.single().type
        assertTrue(
            "PurchaseOrderDetail.lines must be List<PurchaseOrderLine>, found $returnTypeArg",
            returnTypeArg?.classifier == PurchaseOrderLine::class,
        )
    }
}
