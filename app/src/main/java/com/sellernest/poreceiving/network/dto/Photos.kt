package com.sellernest.poreceiving.network.dto

import kotlinx.serialization.Serializable

/** §9.5 `POST /receipts/{id}/photos/` response. The request itself is multipart,
 *  built by the M4.4/M6.1 upload worker rather than modelled as a JSON body. */
@Serializable
data class PhotoUploadResponse(
    val id: Long,
    val originalFilename: String,
    val fileSize: Long,
    val contentType: String,
)
