package com.sellernest.poreceiving.scan

/** Implemented by whichever screen currently owns scan focus. */
fun interface ScanListener {
    fun onScan(code: String, source: ScanSource)
}
