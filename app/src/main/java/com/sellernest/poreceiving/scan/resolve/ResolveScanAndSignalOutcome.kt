package com.sellernest.poreceiving.scan.resolve

import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.ResolveScanRequest
import com.sellernest.poreceiving.network.dto.ScanResolution
import com.sellernest.poreceiving.network.safeApiCall
import com.sellernest.poreceiving.scan.ScanFeedbackService
import kotlinx.serialization.json.Json

/**
 * The one place §9.3's call and §3.1's per-outcome feedback (M0.6) meet:
 * MATCHED signals [ScanFeedbackService.accepted]; every other outcome --
 * including a failed HTTP call, which is not one of the four discriminated
 * outcomes but is still a rejected scan from the receiver's point of view --
 * signals [ScanFeedbackService.rejected]. M3.3's Scan-to-Count screen (not
 * yet built) is the intended caller; kept as a free function rather than a
 * class so that screen's ViewModel can call it directly without another
 * layer of indirection.
 */
suspend fun resolveScanAndSignalOutcome(
    apiService: ApiService,
    json: Json,
    scanFeedbackService: ScanFeedbackService,
    purchaseOrderId: Long,
    code: String,
): ApiResult<ScanResolution> {
    val result = safeApiCall(json) { apiService.resolveScan(purchaseOrderId, ResolveScanRequest(code)) }

    when (result) {
        is ApiResult.Success -> when (result.body) {
            is ScanResolution.Matched -> scanFeedbackService.accepted()
            is ScanResolution.NotOnPurchaseOrder,
            is ScanResolution.MultipleMatches,
            is ScanResolution.UnknownCode,
            -> scanFeedbackService.rejected()
        }

        else -> scanFeedbackService.rejected()
    }

    return result
}
