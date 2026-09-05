package com.sellernest.poreceiving.ui.screens.accessgate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone

/**
 * §5.3's two blocking gates share this shell: a terminal screen with **no**
 * retry control and no path back to sign-in (the nav graph pops Sign In off
 * the back stack before arriving here -- see `PoReceivingNavGraph`).
 */
@Composable
fun AccessBlockedScreen(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateBadge(tone = StateTone.Error, icon = Icons.Filled.Block, label = title)
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

/** §5.3: "Blocking screen: 'Mobile access is not enabled for your account.
 *  Contact your supervisor.' No retry loop, no return to login." */
@Composable
fun MobileAccessDisabledScreen() {
    AccessBlockedScreen(
        title = "ACCESS DISABLED",
        message = "Mobile access is not enabled for your account. Contact your supervisor.",
    )
}

/** §5.3: "Blocking screen: 'This device's access has been revoked.' Clear
 *  local tokens. Drafts are retained." -- the clearing happens in
 *  `SignInViewModel` before navigating here; this screen only informs. */
@Composable
fun DeviceRevokedScreen() {
    AccessBlockedScreen(
        title = "DEVICE REVOKED",
        message = "This device's access has been revoked.",
    )
}
