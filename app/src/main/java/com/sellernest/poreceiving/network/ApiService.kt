package com.sellernest.poreceiving.network

import com.sellernest.poreceiving.network.dto.BinRef
import com.sellernest.poreceiving.network.dto.MeResponse
import com.sellernest.poreceiving.network.dto.PagedResponse
import com.sellernest.poreceiving.network.dto.PhotoUploadResponse
import com.sellernest.poreceiving.network.dto.PurchaseOrderDetail
import com.sellernest.poreceiving.network.dto.PurchaseOrderSummary
import com.sellernest.poreceiving.network.dto.ReceiptSummary
import com.sellernest.poreceiving.network.dto.ReceiveRequest
import com.sellernest.poreceiving.network.dto.ReceiveResponse
import com.sellernest.poreceiving.network.dto.ReconcileRequest
import com.sellernest.poreceiving.network.dto.ReconcileResponse
import com.sellernest.poreceiving.network.dto.ResolveScanRequest
import com.sellernest.poreceiving.network.dto.ScanResolution
import com.sellernest.poreceiving.network.dto.VoidRequest
import com.sellernest.poreceiving.network.dto.VoidResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The full §9 API contract, relative to [ApiConfig.baseUrl]
 * (`/api/mobile/receiving/`). `Authorization` and `X-Active-Org` are never
 * parameters here — they are attached centrally by [AuthInterceptor] and
 * [ActiveOrgInterceptor] (§5.2). Every call site consumes these through
 * [safeApiCall], never a bare Retrofit call.
 */
interface ApiService {

    /**
     * §5.2, §9 preamble. A leading slash resolves against the base URL's host,
     * not its `/api/mobile/receiving/` path -- this endpoint is shared with the
     * web client, not mobile-receiving-specific.
     */
    @GET("/api/me/")
    suspend fun getMe(): Response<MeResponse>

    /** §9.1 */
    @GET("purchase-orders/")
    suspend fun getWorkQueue(
        @Query("warehouse") warehouseId: Long? = null,
        @Query("vendor") vendorId: Long? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): Response<PagedResponse<PurchaseOrderSummary>>

    /** §9.2 */
    @GET("purchase-orders/{id}/")
    suspend fun getPurchaseOrderDetail(@Path("id") purchaseOrderId: Long): Response<PurchaseOrderDetail>

    /** §9.3 — all four outcomes are HTTP 200; see [ScanResolution]. */
    @POST("purchase-orders/{id}/resolve-scan/")
    suspend fun resolveScan(
        @Path("id") purchaseOrderId: Long,
        @Body request: ResolveScanRequest,
    ): Response<ScanResolution>

    /** §9.4 */
    @POST("purchase-orders/{id}/receive/")
    suspend fun receive(
        @Path("id") purchaseOrderId: Long,
        @Body request: ReceiveRequest,
    ): Response<ReceiveResponse>

    /** §7.7/M4.1: reveals expected quantities -- only ever called from the
     *  explicit commit transition, never before. Not in the literal §9
     *  contract; see [ReconcileRequest]'s doc. */
    @POST("purchase-orders/{id}/reconcile/")
    suspend fun reconcile(
        @Path("id") purchaseOrderId: Long,
        @Body request: ReconcileRequest,
    ): Response<ReconcileResponse>

    /** §9.5 — multipart; `file` is required, the other two parts are optional. */
    @Multipart
    @POST("receipts/{id}/photos/")
    suspend fun uploadPhoto(
        @Path("id") receiptId: Long,
        @Part file: MultipartBody.Part,
        @Part("purchase_order_receipt_item") purchaseOrderReceiptItem: Long? = null,
        @Part("caption") caption: String? = null,
    ): Response<PhotoUploadResponse>

    /** M4.6: no exact §9 contract exists for bin lookup; gap-filling design
     *  mirroring [getWorkQueue]'s optional-warehouse-filter shape, which is
     *  what lets a scoped-then-unscoped search (§10's cross-warehouse
     *  rejection) work the same way [com.sellernest.poreceiving.ui.screens.workqueue.WorkQueueViewModel]'s
     *  PO-barcode scan already does. Confirm against the real backend. */
    @GET("bins/")
    suspend fun getBins(
        @Query("warehouse") warehouseId: Long? = null,
        @Query("search") search: String? = null,
    ): Response<PagedResponse<BinRef>>

    /** §9.6 */
    @GET("receipts/")
    suspend fun getReceipts(@Query("date") date: String? = "today"): Response<PagedResponse<ReceiptSummary>>

    /** §9.6 */
    @POST("receipts/{id}/void/")
    suspend fun voidReceipt(
        @Path("id") receiptId: Long,
        @Body request: VoidRequest,
    ): Response<VoidResponse>
}
