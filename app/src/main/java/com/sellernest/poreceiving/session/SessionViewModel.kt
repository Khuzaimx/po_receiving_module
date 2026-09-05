package com.sellernest.poreceiving.session

import androidx.lifecycle.ViewModel
import com.sellernest.poreceiving.auth.SessionInvalidationNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Exposes app-wide session events to the composition root
 * ([com.sellernest.poreceiving.navigation.PoReceivingRoot]), which is the one
 * place with a `NavHostController` able to act on a hard logout (§5.2).
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    val sessionInvalidationNotifier: SessionInvalidationNotifier,
    val meRepository: MeRepository,
) : ViewModel()
