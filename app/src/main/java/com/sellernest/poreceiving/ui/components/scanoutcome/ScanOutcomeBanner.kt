package com.sellernest.poreceiving.ui.components.scanoutcome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sellernest.poreceiving.network.dto.ScanMatchedLine
import com.sellernest.poreceiving.network.dto.ScanResolution
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone

/**
 * §7.6, §9.3: renders exactly the affordances specified for each of the four
 * resolve-scan outcomes -- colour, icon, and text label together (never
 * colour alone), plus each outcome's specific actions. A `when` over the
 * sealed [ScanResolution] means a fifth outcome the server might one day add
 * fails to compile here rather than silently falling through to nothing.
 *
 * Callers: M3.3's Scan-to-Count screen (not yet built) is the real consumer;
 * this component and [resolveScanAndSignalOutcome] exist now so that screen
 * only has to render state, not reimplement outcome handling.
 */
@Composable
fun ScanOutcomeBanner(
    outcome: ScanResolution,
    onScanAgain: () -> Unit,
    onOpenCorrectPo: () -> Unit,
    onEnterSkuManually: () -> Unit,
    onCandidateLineSelected: (purchaseOrderItemId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (outcome) {
        is ScanResolution.Matched -> MatchedOutcome(outcome, modifier)
        is ScanResolution.NotOnPurchaseOrder -> NotOnPurchaseOrderOutcome(outcome, onScanAgain, onOpenCorrectPo, modifier)
        is ScanResolution.MultipleMatches -> MultipleMatchesOutcome(outcome, onCandidateLineSelected, modifier)
        is ScanResolution.UnknownCode -> UnknownCodeOutcome(outcome, onScanAgain, onEnterSkuManually, modifier)
    }
}

@Composable
private fun MatchedOutcome(outcome: ScanResolution.Matched, modifier: Modifier) {
    StateBadge(
        modifier = modifier,
        tone = StateTone.Confirmed,
        icon = Icons.Filled.CheckCircle,
        label = "MATCHED — ${outcome.line.sku}",
    )
}

@Composable
private fun NotOnPurchaseOrderOutcome(
    outcome: ScanResolution.NotOnPurchaseOrder,
    onScanAgain: () -> Unit,
    onOpenCorrectPo: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StateBadge(
            tone = StateTone.Variance,
            icon = Icons.Filled.Warning,
            label = "SKU ${outcome.item.sku} — ${outcome.item.name}. This item is not on this PO.",
        )
        PrimaryButton(text = "SCAN AGAIN", onClick = onScanAgain)
        PrimaryButton(text = "OPEN THE CORRECT PO", onClick = onOpenCorrectPo)
    }
}

@Composable
private fun MultipleMatchesOutcome(
    outcome: ScanResolution.MultipleMatches,
    onCandidateLineSelected: (purchaseOrderItemId: Long) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StateBadge(
            tone = StateTone.Variance,
            icon = Icons.Filled.Warning,
            label = "MULTIPLE MATCHES — choose the correct line",
        )
        // §7.6: "Lists candidate lines; the receiver chooses. Never
        // auto-select." There is no default selection and no path that picks
        // one for the receiver -- every row requires an explicit tap.
        outcome.lines.forEach { candidate: ScanMatchedLine ->
            Text(
                text = "${candidate.sku} — ${candidate.name}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCandidateLineSelected(candidate.purchaseOrderItemId) }
                    .padding(vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun UnknownCodeOutcome(
    outcome: ScanResolution.UnknownCode,
    onScanAgain: () -> Unit,
    onEnterSkuManually: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StateBadge(
            tone = StateTone.Error,
            icon = Icons.Filled.Error,
            label = "UNKNOWN CODE",
        )
        // §7.6: "Shows the raw scanned string (essential for support)," and
        // per M2.6's acceptance criteria, with unprintable characters
        // rendered visibly rather than disappearing as blank glyphs -- see
        // [renderableScanCode]. Kept as its own Text, not folded into the
        // badge label, so an unusually long string is never clipped by the
        // badge's layout.
        Text(text = renderableScanCode(outcome.code), style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(text = "SCAN AGAIN", onClick = onScanAgain)
        PrimaryButton(text = "ENTER SKU MANUALLY", onClick = onEnterSkuManually)
    }
}

/**
 * Maps ASCII control characters (0x00-0x1F, 0x7F) to their visible Unicode
 * "Control Pictures" glyphs (U+2400-U+2421) so a code containing one is never
 * silently blank in the UI -- the raw [ScanResolution.UnknownCode.code] value
 * itself is untouched; this only affects what's displayed.
 */
internal fun renderableScanCode(raw: String): String = buildString {
    for (char in raw) {
        val code = char.code
        when {
            code in 0x00..0x1F -> append(Char(0x2400 + code))
            code == 0x7F -> append(Char(0x2421))
            else -> append(char)
        }
    }
}
