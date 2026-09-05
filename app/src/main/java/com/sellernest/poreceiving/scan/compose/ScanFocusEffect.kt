package com.sellernest.poreceiving.scan.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.sellernest.poreceiving.scan.ScanDispatcher
import com.sellernest.poreceiving.scan.ScanListener
import com.sellernest.poreceiving.scan.ScanSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ScanDispatcherEntryPoint {
    fun scanDispatcher(): ScanDispatcher
}

@Composable
internal fun rememberScanDispatcher(): ScanDispatcher {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, ScanDispatcherEntryPoint::class.java).scanDispatcher()
    }
}

/**
 * The **only** sanctioned way a screen takes scan focus (§4.1). Registers on
 * entering composition and unregisters on leaving it, so navigating away
 * always yields focus back cleanly -- no screen should call
 * [ScanDispatcher.register] directly; see `NoDirectScanDispatcherAccessTest`.
 */
@Composable
fun ScanFocusEffect(onScan: (code: String, source: ScanSource) -> Unit) {
    val dispatcher = rememberScanDispatcher()
    val currentOnScan = rememberUpdatedState(onScan)

    DisposableEffect(dispatcher) {
        val listener = ScanListener { code, source -> currentOnScan.value(code, source) }
        dispatcher.register(listener)
        onDispose { dispatcher.unregister(listener) }
    }
}
