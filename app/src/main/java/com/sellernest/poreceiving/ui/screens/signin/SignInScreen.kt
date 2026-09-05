package com.sellernest.poreceiving.ui.screens.signin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.BuildConfig
import com.sellernest.poreceiving.network.ApiConfig
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.PoReceivingTheme
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone

/**
 * §7.1. One button; no username/password field anywhere -- credentials are
 * entered in the browser (§5.1). No "create account", no "forgot password":
 * all provisioning happens in the web admin UI (§1.3).
 */
@Composable
fun SignInScreen(viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onEvent(SignInUiEvent.AuthorizationResultReceived(result.data))
    }

    SignInScreenContent(
        state = state,
        onSignInTapped = {
            viewModel.onEvent(SignInUiEvent.SignInTapped)
            signInLauncher.launch(viewModel.buildSignInIntent())
        },
    )
}

/** Internal, not private: exercised directly by `SignInScreenInstrumentedTest`
 *  (androidTest can see `internal` `main` declarations) so screen-content tests
 *  don't need a Hilt-backed `SignInViewModel`. */
@Composable
internal fun SignInScreenContent(state: SignInUiState, onSignInTapped: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "PO RECEIVING", style = MaterialTheme.typography.headlineLarge)

        PrimaryButton(
            text = if (state.isSigningIn) "SIGNING IN..." else "SIGN IN",
            enabled = !state.isSigningIn,
            onClick = onSignInTapped,
        )

        state.errorMessage?.let { message ->
            StateBadge(tone = StateTone.Error, icon = Icons.Filled.Error, label = message)
        }

        // Server host and app version are shown because they are the first
        // things support asks for (§7.1).
        Text(text = "Server: ${ApiConfig.displayHost}", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "App version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(name = "Portrait", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SignInScreenPortraitPreview() {
    PoReceivingTheme { SignInScreenContent(state = SignInUiState(), onSignInTapped = {}) }
}

@Preview(name = "Landscape", showBackground = true, widthDp = 800, heightDp = 360)
@Composable
private fun SignInScreenLandscapePreview() {
    PoReceivingTheme { SignInScreenContent(state = SignInUiState(), onSignInTapped = {}) }
}

@Preview(
    name = "Largest font scale",
    showBackground = true,
    widthDp = 360,
    heightDp = 800,
    fontScale = 2.0f,
)
@Composable
private fun SignInScreenLargeFontScalePreview() {
    PoReceivingTheme { SignInScreenContent(state = SignInUiState(), onSignInTapped = {}) }
}
