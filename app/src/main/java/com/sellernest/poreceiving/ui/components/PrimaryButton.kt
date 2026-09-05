package com.sellernest.poreceiving.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sellernest.poreceiving.ui.theme.PoReceivingColors
import com.sellernest.poreceiving.ui.theme.TouchTarget

/**
 * The full-width primary action control used throughout §7 (SIGN IN, START RECEIVING,
 * COMMIT COUNT, SUBMIT RECEIPT, ...). Always ≥ 56 dp tall (§3.1 primary touch target)
 * and never a floating action button — per §7.5, a primary action here is a
 * deliberate, full-width control that must not be pressed accidentally.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(TouchTarget.primary)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = PoReceivingColors.primary,
            contentColor = PoReceivingColors.onPrimary,
            disabledContainerColor = PoReceivingColors.outline,
            disabledContentColor = PoReceivingColors.onPrimary,
        ),
    ) {
        Text(text = text)
    }
}
