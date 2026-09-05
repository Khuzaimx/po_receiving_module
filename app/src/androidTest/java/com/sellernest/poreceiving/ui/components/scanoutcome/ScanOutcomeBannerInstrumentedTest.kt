package com.sellernest.poreceiving.ui.components.scanoutcome

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sellernest.poreceiving.network.dto.ScanMatchedLine
import com.sellernest.poreceiving.network.dto.ScanResolution
import com.sellernest.poreceiving.network.dto.UnmatchedItem
import com.sellernest.poreceiving.ui.theme.PoReceivingTheme
import org.junit.Rule
import org.junit.Test

/**
 * M2.6 acceptance criteria: each outcome renders its specified label, and
 * "never auto-select" for MULTIPLE_MATCHES -- no candidate line is chosen
 * without an explicit tap.
 */
class ScanOutcomeBannerInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val matchedLine = ScanMatchedLine(
        purchaseOrderItemId = 88213,
        sku = "WM-4410-BLK",
        name = "Widget Mount, Black",
        quantityAlreadyReceived = 2,
        requiresSerialNumber = false,
        fullyReceived = false,
    )

    @Test
    fun matchedShowsTheSku() {
        setBanner(ScanResolution.Matched(matchedField = "upc", line = matchedLine))
        composeTestRule.onNodeWithText("MATCHED — WM-4410-BLK").assertExists()
    }

    @Test
    fun notOnPurchaseOrderNamesTheItemAndOffersBothActions() {
        setBanner(ScanResolution.NotOnPurchaseOrder(UnmatchedItem(sku = "CB-9911", name = "Cable Brace 9911")))

        composeTestRule.onNodeWithText(
            "SKU CB-9911 — Cable Brace 9911. This item is not on this PO.",
        ).assertExists()
        composeTestRule.onNodeWithText("SCAN AGAIN").assertExists()
        composeTestRule.onNodeWithText("OPEN THE CORRECT PO").assertExists()
    }

    @Test
    fun unknownCodeShowsTheRawStringAndBothActions() {
        setBanner(ScanResolution.UnknownCode(code = "0468673502897"))

        composeTestRule.onNodeWithText("UNKNOWN CODE").assertExists()
        composeTestRule.onNodeWithText("0468673502897").assertExists()
        composeTestRule.onNodeWithText("SCAN AGAIN").assertExists()
        composeTestRule.onNodeWithText("ENTER SKU MANUALLY").assertExists()
    }

    @Test
    fun multipleMatchesListsEveryCandidateAndNeverAutoSelects() {
        var selected: Long? = null
        val candidateA = matchedLine.copy(purchaseOrderItemId = 1, sku = "A", name = "Candidate A")
        val candidateB = matchedLine.copy(purchaseOrderItemId = 2, sku = "B", name = "Candidate B")

        composeTestRule.setContent {
            PoReceivingTheme {
                ScanOutcomeBanner(
                    outcome = ScanResolution.MultipleMatches(listOf(candidateA, candidateB)),
                    onScanAgain = {},
                    onOpenCorrectPo = {},
                    onEnterSkuManually = {},
                    onCandidateLineSelected = { selected = it },
                )
            }
        }

        composeTestRule.onNodeWithText("A — Candidate A").assertExists()
        composeTestRule.onNodeWithText("B — Candidate B").assertExists()
        // Nothing has been tapped yet: no auto-selection happened merely from rendering.
        assert(selected == null) { "Expected no candidate to be auto-selected" }

        composeTestRule.onNodeWithText("B — Candidate B").performClick()
        assert(selected == 2L) { "Expected tapping candidate B to select purchaseOrderItemId 2" }
    }

    private fun setBanner(outcome: ScanResolution) {
        composeTestRule.setContent {
            PoReceivingTheme {
                ScanOutcomeBanner(
                    outcome = outcome,
                    onScanAgain = {},
                    onOpenCorrectPo = {},
                    onEnterSkuManually = {},
                    onCandidateLineSelected = {},
                )
            }
        }
    }
}
