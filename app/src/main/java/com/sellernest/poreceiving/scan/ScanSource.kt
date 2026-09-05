package com.sellernest.poreceiving.scan

/** §4.1: the three input paths that all feed the single [ScanDispatcher]. */
enum class ScanSource {
    HARDWARE_INTENT,
    KEYBOARD_WEDGE,
    CAMERA,
}
