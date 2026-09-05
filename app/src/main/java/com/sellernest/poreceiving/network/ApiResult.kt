package com.sellernest.poreceiving.network

import com.sellernest.poreceiving.network.dto.ApiErrorBody
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Every API call site returns this instead of a raw Retrofit [Response] or a
 * thrown exception, so transport failure, an HTTP error body, and the specific
 * 409 idempotency conflict are three distinct, exhaustively-handleable cases
 * rather than a mix of exceptions and nullable bodies.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val body: T) : ApiResult<T>

    /** Any non-2xx response other than the 409 conflict, with its parsed error body. */
    data class HttpError(val code: Int, val error: ApiErrorBody?) : ApiResult<Nothing>

    /** §9.4's `409 idempotency_key_conflict` — the one HTTP error every submit
     *  caller must handle distinctly (M5.4), never lump in with [HttpError]. */
    data class IdempotencyConflict(val detail: String?) : ApiResult<Nothing>

    /** No response reached the app at all: no connectivity, timeout, DNS, etc. */
    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>
}

/**
 * Wraps a Retrofit suspend call, translating its outcome into [ApiResult].
 * This is the only place `Response.errorBody()` is parsed — call sites never
 * touch it directly.
 */
suspend fun <T> safeApiCall(json: Json, call: suspend () -> Response<T>): ApiResult<T> {
    return try {
        val response = call()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.HttpError(response.code(), null)
            }
        } else {
            val errorBody = response.errorBody()?.string()
            val parsedError = errorBody?.let {
                runCatching { json.decodeFromString(ApiErrorBody.serializer(), it) }.getOrNull()
            }
            if (response.code() == 409 && parsedError?.error == "idempotency_key_conflict") {
                ApiResult.IdempotencyConflict(parsedError.detail)
            } else {
                ApiResult.HttpError(response.code(), parsedError)
            }
        }
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        ApiResult.NetworkError(t)
    }
}
