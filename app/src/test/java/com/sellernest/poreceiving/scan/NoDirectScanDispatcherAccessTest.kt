package com.sellernest.poreceiving.scan

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M2.1: "No screen may implement its own scan handling." Every screen must go
 * through [com.sellernest.poreceiving.scan.compose.ScanFocusEffect]; nothing
 * outside the `scan` package itself may call `ScanDispatcher.register`/
 * `.unregister` directly.
 */
class NoDirectScanDispatcherAccessTest {

    private val callPattern = Regex("""\.(register|unregister)\(""")

    @Test
    fun `no source file outside scan package calls register or unregister directly`() {
        val candidates = listOf(File("src/main/java"), File("app/src/main/java"))
        val mainRoot = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate src/main/java from working dir ${File(".").absolutePath}")

        val offenders = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.replace('\\', '/').contains("/scan/") }
            .filter { file -> callPattern.containsMatchIn(file.readText()) }
            .map { it.relativeTo(mainRoot).path }
            .toList()

        assertTrue(
            "Found direct ScanDispatcher register/unregister calls outside scan/: $offenders",
            offenders.isEmpty(),
        )
    }
}
