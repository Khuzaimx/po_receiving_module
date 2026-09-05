package com.sellernest.poreceiving.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sellernest.poreceiving.core.connectivity.ConnectivityObserver
import com.sellernest.poreceiving.data.local.dao.QueuedSubmissionDao
import com.sellernest.poreceiving.session.SessionInfoProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatusBarUiState(
    val isOnline: Boolean = true,
    val pendingSubmissionCount: Int = 0,
    val activeWarehouseName: String? = null,
    val signedInUserLabel: String? = null,
)

/**
 * Backs the persistent status bar (§3.2). Deliberately a plain [ViewModel], not
 * [com.sellernest.poreceiving.core.mvvm.BaseViewModel]: the status bar has no
 * user-triggered events, only observed state, so there is nothing for a
 * `UiEvent` sealed type to represent.
 */
@HiltViewModel
class StatusBarViewModel @Inject constructor(
    connectivityObserver: ConnectivityObserver,
    queuedSubmissionDao: QueuedSubmissionDao,
    sessionInfoProvider: SessionInfoProvider,
) : ViewModel() {

    val state: StateFlow<StatusBarUiState> = combine(
        connectivityObserver.isOnline,
        queuedSubmissionDao.observePendingCount(),
        sessionInfoProvider.observe(),
    ) { isOnline, pendingCount, session ->
        StatusBarUiState(
            isOnline = isOnline,
            pendingSubmissionCount = pendingCount,
            activeWarehouseName = session.activeWarehouseName,
            signedInUserLabel = session.signedInUserLabel,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatusBarUiState(),
    )
}
