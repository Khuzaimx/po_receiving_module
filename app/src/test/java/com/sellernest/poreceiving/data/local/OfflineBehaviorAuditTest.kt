package com.sellernest.poreceiving.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M5.6: "Implement and verify the complete §8 table as a single coherent
 * behaviour, not as scattered special cases." Most §8 rows already have a
 * dedicated behavioural test elsewhere; this class is the audit tying every
 * row to where that proof actually lives, plus two source-scanned guardrails
 * for the two rows that are absences (nothing auto-expires a draft, nothing
 * pre-downloads a PO) rather than a positive behaviour a normal test can
 * exercise.
 *
 * | §8 row | Covered by |
 * |---|---|
 * | Scan while offline works normally | [com.sellernest.poreceiving.ui.screens.scantocount.ScanToCountViewModelTest] -- `recordScan`/`setLineQuantity` never call the network (`DraftRepositoryTest`'s scan/quantity tests, too) |
 * | Every scan written to Room immediately, survives process death/reboot | `DraftPersistenceInstrumentedTest.twentyScansAcrossTenLinesSurviveSimulatedProcessDeathWithCorrectQuantities` |
 * | Open a PO while offline fails clearly | `PoHeaderViewModelTest`'s "a network failure shows the specific connect-to-load message" |
 * | Submit while offline is queued via WorkManager with backoff | `com.sellernest.poreceiving.work.submit.SubmitDraftUseCaseTest`'s NetworkError -> Retry test, plus `WorkRequestFactory`'s `requiresConnection` constraint |
 * | Retry reuses the same idempotency key | `SubmitDraftUseCaseTest`'s idempotency-key tests |
 * | App killed mid-count offers RESUME DRAFT with the scan count | `PoHeaderViewModelTest`'s existing-draft/resume tests |
 * | Draft retention: never auto-expired | [draftRepositoryNeverExpiresAnythingByAge] below (a guardrail, not a positive test -- there is nothing to exercise) |
 * | Network lost mid-count: status bar OFFLINE, counting continues | `com.sellernest.poreceiving.core.connectivity.AndroidConnectivityObserver` backs `StatusBarViewModel` independently of `DraftRepository`, which never reads it -- structurally, counting cannot be affected by connectivity |
 * | Killing the app mid-retry / a reboot doesn't lose the queued submission | `DraftPersistenceInstrumentedTest.queuedSubmissionSurvivesSimulatedProcessDeath` |
 * | Nothing pre-downloads POs (§1.3 scope exclusion) | [nothingInTheAppBulkCachesPurchaseOrders] below |
 */
class OfflineBehaviorAuditTest {

    @Test
    fun draftRepositoryNeverExpiresAnythingByAge() {
        val source = readSource("data/local/DraftRepository.kt")
        // §8: "Retained until submitted or explicitly discarded. Never
        // auto-expired." -- there is no time-based cleanup path at all.
        val forbidden = listOf("expire", "Expire", "TTL", "maxAge", "olderThan")
        val offenders = forbidden.filter { source.contains(it) }
        assertTrue("Found an apparent auto-expiry mechanism in DraftRepository: $offenders", offenders.isEmpty())
    }

    @Test
    fun nothingInTheAppBulkCachesPurchaseOrders() {
        // §1.3: "The app keeps a local draft and queues its submit; it does
        // not download purchase orders for wholly offline work." The only
        // PO-shaped local storage is the per-draft snapshot the receiver is
        // actively counting (DraftEntity/DraftLineEntity) -- there is no
        // "PurchaseOrder" or "PurchaseOrderLine" Room entity/table at all.
        val mainSourceDir = mainSourceRoot()
        val entityFiles = mainSourceDir.resolve("data/local/entities")
            .listFiles { file -> file.extension == "kt" }
            .orEmpty()
            .map { it.name }

        assertFalse(
            "Found a Room entity that looks like a bulk PO cache: $entityFiles",
            entityFiles.any { it.contains("PurchaseOrder") },
        )
    }

    private fun readSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/com/sellernest/poreceiving/$relativePath"),
            File("app/src/main/java/com/sellernest/poreceiving/$relativePath"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate $relativePath from working dir ${File(".").absolutePath}")
        return file.readText()
    }

    private fun mainSourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/sellernest/poreceiving"),
            File("app/src/main/java/com/sellernest/poreceiving"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate the main source root from working dir ${File(".").absolutePath}")
    }
}
