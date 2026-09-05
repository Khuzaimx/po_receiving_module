package com.sellernest.poreceiving.network

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * §5.2: "Attach it in an OkHttp interceptor, never per-call." This scans every
 * Kotlin source file under `src/main` for a header-setting call
 * (`addHeader(...)` / `header(...)`) that names `Authorization` or
 * `X-Active-Org`, and fails if one exists anywhere other than the two files that
 * are allowed to: [AuthInterceptor] and [ActiveOrgInterceptor].
 *
 * A plain text search for the header name would false-positive on this file's own
 * doc comments, so the pattern requires the literal to appear as the argument of a
 * header-setting call.
 */
class InterceptorHeaderOwnershipTest {

    private val allowedFiles = setOf("AuthInterceptor.kt", "ActiveOrgInterceptor.kt")
    // No leading `.` required: inside an `apply { }`/`with { }` block (as both
    // interceptors themselves call it) the receiver is implicit, so a call site
    // reads as bare `addHeader(...)`, not `chain.addHeader(...)`.
    private val headerCallPattern = Regex("\\b(addHeader|header)\\(\\s*\"(Authorization|X-Active-Org)\"")

    private fun findMainSourceRoot(): File {
        val candidates = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate src/main/java from working dir ${File(".").absolutePath}")
    }

    @Test
    fun `no source file outside the two interceptors sets Authorization or X-Active-Org directly`() {
        val root = findMainSourceRoot()
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name in allowedFiles }
            .filter { file -> headerCallPattern.containsMatchIn(file.readText()) }
            .map { it.relativeTo(root).path }
            .toList()

        assertTrue(
            "Found direct Authorization/X-Active-Org header calls outside the interceptors: $offenders",
            offenders.isEmpty(),
        )
    }
}
