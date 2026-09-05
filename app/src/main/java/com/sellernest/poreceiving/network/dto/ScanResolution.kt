package com.sellernest.poreceiving.network.dto

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** §9.3 request body: `{"code": "<scanned string>"}`. */
@Serializable
data class ResolveScanRequest(val code: String)

/** The line returned on a match, or as one of several candidates (§9.3). */
@Serializable
data class ScanMatchedLine(
    val purchaseOrderItemId: Long,
    val sku: String,
    val name: String,
    val quantityAlreadyReceived: Int,
    val requiresSerialNumber: Boolean,
    val fullyReceived: Boolean,
)

/** The item named on a not-on-PO outcome (§9.3, §7.6: "Amber... Name the item."). */
@Serializable
data class UnmatchedItem(
    val sku: String,
    val name: String,
)

/**
 * `POST /purchase-orders/{id}/resolve-scan/` response (§9.3).
 *
 * All four outcomes are HTTP 200 with an `outcome` discriminator — "a non-matching
 * scan is routine operational flow, not a client error, and must not be modelled
 * as a 404." This sealed type makes that structural: there is no generic
 * "error" branch here, and a caller (M2.6) must exhaustively handle all four in a
 * `when`. Full UI behaviour per outcome is M2.6's concern; this is the wire model.
 */
@Serializable(with = ScanResolutionSerializer::class)
sealed interface ScanResolution {
    @Serializable
    data class Matched(val matchedField: String, val line: ScanMatchedLine) : ScanResolution

    @Serializable
    data class NotOnPurchaseOrder(val item: UnmatchedItem) : ScanResolution

    @Serializable
    data class MultipleMatches(val lines: List<ScanMatchedLine>) : ScanResolution

    @Serializable
    data class UnknownCode(val code: String) : ScanResolution
}

internal object ScanResolutionSerializer :
    JsonContentPolymorphicSerializer<ScanResolution>(ScanResolution::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ScanResolution> {
        val outcome = element.jsonObject["outcome"]?.jsonPrimitive?.content
        return when (outcome) {
            "matched" -> ScanResolution.Matched.serializer()
            "not_on_purchase_order" -> ScanResolution.NotOnPurchaseOrder.serializer()
            "multiple_matches" -> ScanResolution.MultipleMatches.serializer()
            "unknown_code" -> ScanResolution.UnknownCode.serializer()
            else -> throw SerializationException("Unknown resolve-scan outcome: $outcome")
        }
    }
}
