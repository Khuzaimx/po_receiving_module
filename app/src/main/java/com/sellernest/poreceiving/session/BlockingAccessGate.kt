package com.sellernest.poreceiving.session

import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.dto.MeResponse

/**
 * §5.3's two *blocking* gates -- "mobile access not enabled" and "device
 * revoked." Both are server-enforced and detected from `/api/me/`'s error body;
 * this app only reflects them.
 *
 * The other two rows in §5.3's table are not blocking screens and are handled
 * where they actually surface: "lacks receiving permission" is the work queue's
 * empty state (M1.7, reading [MeSessionState.permissions]), and
 * "warehouse-scoped permission" is M1.5's selector only ever showing the
 * warehouses `/api/me/` already returned.
 *
 * Note on scope: like `/api/me/`'s success shape, these two error codes are a
 * design filling a gap the spec never gives an exact contract for -- confirm
 * the real error codes against the backend before this ships.
 */
enum class BlockingAccessGate {
    MobileAccessDisabled,
    DeviceRevoked,
}

/** Null when [result] isn't one of the two recognised blocking gates -- the
 *  caller should fall through to its normal error handling in that case. */
fun blockingAccessGateFor(result: ApiResult<MeResponse>): BlockingAccessGate? {
    if (result !is ApiResult.HttpError || result.code != 403) return null
    return when (result.error?.error) {
        "mobile_access_disabled" -> BlockingAccessGate.MobileAccessDisabled
        "device_revoked" -> BlockingAccessGate.DeviceRevoked
        else -> null
    }
}
