package com.sellernest.poreceiving.scan

import javax.inject.Inject
import javax.inject.Singleton

/**
 * §4.1: "Hardware trigger and camera must both dispatch into a single
 * onScan(code, source) entry point. No screen may implement its own scan
 * handling." Every future input source (DataWedge in M2.2, keyboard wedge in
 * M2.3, CameraX+ML Kit in M2.4) calls [dispatch]; every screen registers via
 * [com.sellernest.poreceiving.scan.compose.ScanFocusEffect], never directly.
 *
 * At most one listener is ever "current" -- registering a new one silently
 * replaces whatever was registered before, so exactly one screen receives a
 * scan even if several are technically alive in the back stack.
 *
 * §4.2's three hygiene rules are enforced here, centrally, so no input source
 * or screen can bypass them: terminators/whitespace are stripped before a code
 * is ever compared or forwarded, and an identical code within
 * [DEBOUNCE_WINDOW_MS] of the previous *accepted* scan is silently dropped
 * (a camera decodes the same barcode many times a second while it's in frame).
 * "Never auto-advance on ambiguity" is a UI-layer rule instead -- see M2.6's
 * `MultipleMatches` handling, which always requires an explicit choice.
 */
@Singleton
class ScanDispatcher @Inject constructor() {

    @Volatile
    private var currentOwner: ScanListener? = null

    @Volatile
    private var lastAcceptedCode: String? = null

    @Volatile
    private var lastAcceptedAtMillis: Long = 0L

    fun register(listener: ScanListener) {
        currentOwner = listener
    }

    /** No-ops if [listener] is not the current owner -- an already-superseded
     *  screen unregistering on disposal must never clear the new owner. */
    fun unregister(listener: ScanListener) {
        if (currentOwner === listener) {
            currentOwner = null
        }
    }

    fun dispatch(rawCode: String, source: ScanSource) {
        val code = sanitizeScanCode(rawCode)
        if (code.isEmpty()) return

        val now = System.currentTimeMillis()
        if (isDuplicateWithinDebounceWindow(code, lastAcceptedCode, now, lastAcceptedAtMillis)) return

        lastAcceptedCode = code
        lastAcceptedAtMillis = now
        currentOwner?.onScan(code, source)
    }
}

/** §4.2: "Strip terminators. Trailing \r, \n, and surrounding whitespace
 *  before dispatch." A plain `trim()` covers all three -- `\r`/`\n` are
 *  themselves whitespace characters, so trimming whitespace is trimming
 *  terminators. A free function so it's unit-testable without a dispatcher
 *  instance. */
internal fun sanitizeScanCode(raw: String): String = raw.trim()

/** §4.2: "Reject an identical code within 800 ms of the previous accepted
 *  scan." Takes the clock reading as a parameter rather than calling
 *  [System.currentTimeMillis] itself, so the decision is testable with exact,
 *  reproducible timings. */
internal fun isDuplicateWithinDebounceWindow(
    code: String,
    lastAcceptedCode: String?,
    nowMillis: Long,
    lastAcceptedAtMillis: Long,
): Boolean = code == lastAcceptedCode && (nowMillis - lastAcceptedAtMillis) < DEBOUNCE_WINDOW_MS

internal const val DEBOUNCE_WINDOW_MS = 800L
