package com.sellernest.poreceiving

import com.sellernest.poreceiving.ui.theme.TouchTarget
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.2: "Instrument the scan pipeline and record p50/p95 scan-to-feedback
 * latency in CI on a reference device," "battery endurance run on a Zebra
 * TC-series device," and "usable in direct sunlight" are physical-hardware
 * measurements. This environment has no JDK, Android SDK, emulator, or
 * physical device -- see the standing caveat repeated in every PR from this
 * session -- so none of the three can actually be produced here. What this
 * class *can* verify, from source alone, are the two rows that are
 * structural/code-level properties rather than measurements:
 *
 * - Every sanctioned touch-target token is >= 48 dp (§3.1).
 * - Every screen that accepts a scan wires the hardware-trigger path
 *   ([com.sellernest.poreceiving.scan.compose.ScanFocusEffect] +
 *   [com.sellernest.poreceiving.scan.compose.KeyboardWedgeCapture]), so "full
 *   operation by hardware trigger alone, camera never opened" holds by
 *   construction rather than by a scripted manual pass this session cannot run.
 *
 * The remaining rows -- p50/p95 latency, the 8-hour endurance run, contrast
 * and sunlight legibility, and the actual camera-revoked manual pass -- need
 * to be executed on real hardware before this milestone can be marked done;
 * they are out of reach of a JUnit test in any environment, not just this one.
 */
class NonFunctionalAuditTest {

    @Test
    fun everySanctionedTouchTargetTokenMeetsTheFortyEightDpFloor() {
        assertTrue("TouchTarget.minimum must be >= 48dp per §3.1", TouchTarget.minimum.value >= 48f)
        assertTrue("TouchTarget.primary must be >= 48dp per §3.1", TouchTarget.primary.value >= 48f)
    }

    @Test
    fun everyScanCapableScreenWiresBothTheFocusEffectAndTheKeyboardWedge() {
        val scanCapableScreens = listOf(
            "ui/screens/workqueue/WorkQueueScreen.kt",
            "ui/screens/scantocount/ScanToCountScreen.kt",
            "ui/screens/serialcapture/SerialCaptureScreen.kt",
            "ui/screens/binconfirmation/BinConfirmationScreen.kt",
        )
        val missing = scanCapableScreens.filter { path ->
            val source = readSource(path)
            !source.contains("ScanFocusEffect") || !source.contains("KeyboardWedgeCapture")
        }
        assertTrue("Screens missing hardware-trigger wiring (ScanFocusEffect/KeyboardWedgeCapture): $missing", missing.isEmpty())
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
}
