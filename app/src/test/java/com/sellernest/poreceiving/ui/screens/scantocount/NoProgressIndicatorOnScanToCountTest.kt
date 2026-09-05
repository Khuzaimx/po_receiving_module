package com.sellernest.poreceiving.ui.screens.scantocount

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M3.1/M3.3: "No expected quantity, no progress bar, no 'x of y' anywhere on
 * this screen." §11: "If a future build shows '7 of 12 scanned' on the
 * counting screen, blind counting has been silently defeated." Source-scanned
 * rather than UI-tested, so it fails the build the instant a future edit adds
 * any of this -- no need to wait for a manual review to notice.
 */
class NoProgressIndicatorOnScanToCountTest {

    private val forbiddenSubstrings = listOf(
        "quantityExpected",
        "expectedQuantity",
        "totalExpected",
        "ExpectedQuantity",
        "ProgressIndicator",
        "LinearProgress",
        "CircularProgress",
    )

    @Test
    fun `the Scan-to-Count screen source contains no expected-quantity or progress-indicator reference`() {
        val candidates = listOf(
            File("src/main/java/com/sellernest/poreceiving/ui/screens/scantocount"),
            File("app/src/main/java/com/sellernest/poreceiving/ui/screens/scantocount"),
        )
        val packageDir = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate the scantocount package from working dir ${File(".").absolutePath}")

        val sourceFiles = packageDir.listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue("Expected to find ScanToCount source files", sourceFiles.isNotEmpty())

        val offenders = mutableListOf<String>()
        for (file in sourceFiles) {
            val text = file.readText()
            for (pattern in forbiddenSubstrings) {
                if (text.contains(pattern)) offenders.add("${file.name}: $pattern")
            }
        }

        assertFalse("Found forbidden expected-quantity/progress-indicator references: $offenders", offenders.isNotEmpty())
    }
}
