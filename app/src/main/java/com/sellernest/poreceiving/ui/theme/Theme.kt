package com.sellernest.poreceiving.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val IndustrialColorScheme = lightColorScheme(
    primary = PoReceivingColors.primary,
    onPrimary = PoReceivingColors.onPrimary,
    background = PoReceivingColors.background,
    onBackground = PoReceivingColors.onSurface,
    surface = PoReceivingColors.surface,
    onSurface = PoReceivingColors.onSurface,
    surfaceVariant = PoReceivingColors.surfaceVariant,
    onSurfaceVariant = PoReceivingColors.onSurface,
    error = PoReceivingColors.toneError,
    outline = PoReceivingColors.outline,
)

/**
 * The app's one and only theme. There is deliberately no dark-theme branch
 * keyed off the system setting — requirements §3.1: "Dark mode: Not in the
 * first release. A single high-contrast light theme is correct for sunlit
 * docks and halves the QA surface." (See `ScopeComplianceAuditTest` for the
 * guardrail this absence is checked against.)
 */
@Composable
fun PoReceivingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IndustrialColorScheme,
        typography = PoReceivingTypography,
        content = content,
    )
}
