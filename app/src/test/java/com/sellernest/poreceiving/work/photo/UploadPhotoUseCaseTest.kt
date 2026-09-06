package com.sellernest.poreceiving.work.photo

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.FakeDraftPhotoDao
import com.sellernest.poreceiving.data.local.FakeDraftSerialDao
import com.sellernest.poreceiving.data.local.FakeQueuedSubmissionDao
import com.sellernest.poreceiving.data.local.entities.DraftPhotoEntity
import com.sellernest.poreceiving.data.local.entities.QueuedSubmissionEntity
import com.sellernest.poreceiving.data.local.entities.SubmissionStatus
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.ui.screens.poheader.FakeDraftLineDao
import com.sellernest.poreceiving.ui.screens.warehouseselection.FakeDraftDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.io.File

/** §9.5/§10/M6.1 acceptance criteria. */
class UploadPhotoUseCaseTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var json: Json
    private lateinit var draftPhotoDao: FakeDraftPhotoDao
    private lateinit var queuedSubmissionDao: FakeQueuedSubmissionDao
    private lateinit var draftRepository: DraftRepository
    private var draftId: Long = 0
    private lateinit var tempFile: File

    @Before
    fun setUp() = runTest {
        server = MockWebServer()
        server.start()
        json = Json {
            namingStrategy = JsonNamingStrategy.SnakeCase
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/mobile/receiving/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        apiService = retrofit.create(ApiService::class.java)
        draftPhotoDao = FakeDraftPhotoDao()
        queuedSubmissionDao = FakeQueuedSubmissionDao()
        val draftLineDao = FakeDraftLineDao()
        draftRepository = DraftRepository(FakeDraftDao(), draftLineDao, draftPhotoDao, FakeDraftSerialDao(draftLineDao), queuedSubmissionDao)

        val draft = draftRepository.openPurchaseOrder(10482, "PO-10482", warehouseId = 2)
        draftId = draft.id
        tempFile = File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempFile.delete()
    }

    private fun useCase() = UploadPhotoUseCase(draftRepository, apiService, json)

    @Test
    fun `upload is retried, not attempted, until the receipt id is known`() = runTest {
        val photoId = draftPhotoDao.insert(DraftPhotoEntity(draftId = draftId, localFilePath = tempFile.path))
        // No QueuedSubmissionEntity at all yet -- receipt not posted.

        val outcome = useCase().execute(photoId)

        assertEquals(UploadPhotoOutcome.Retry, outcome)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a successful upload marks the photo uploaded and deletes the local file`() = runTest {
        val photoId = draftPhotoDao.insert(DraftPhotoEntity(draftId = draftId, localFilePath = tempFile.path))
        queuedSubmissionDao.upsert(
            QueuedSubmissionEntity(draftId = draftId, status = SubmissionStatus.RECEIPTED, receiptId = 4412, enqueuedAtEpochMillis = 0),
        )
        server.enqueue(MockResponse().setBody("""{"id": 91, "original_filename": "photo.jpg", "file_size": 3, "content_type": "image/jpeg"}"""))

        val outcome = useCase().execute(photoId)

        assertEquals(UploadPhotoOutcome.Success, outcome)
        assertEquals(true, draftPhotoDao.getById(photoId)?.uploaded)
        assertEquals(91L, draftPhotoDao.getById(photoId)?.remotePhotoId)
        assertTrue("local file should be deleted after a successful upload", !tempFile.exists())
    }

    @Test
    fun `a rejected upload keeps the local file so nothing is lost`() = runTest {
        val photoId = draftPhotoDao.insert(DraftPhotoEntity(draftId = draftId, localFilePath = tempFile.path))
        queuedSubmissionDao.upsert(
            QueuedSubmissionEntity(draftId = draftId, status = SubmissionStatus.RECEIPTED, receiptId = 4412, enqueuedAtEpochMillis = 0),
        )
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error": "unsupported_file_type"}"""))

        val outcome = useCase().execute(photoId)

        assertEquals(UploadPhotoOutcome.Failure, outcome)
        assertEquals(false, draftPhotoDao.getById(photoId)?.uploaded)
        assertTrue(tempFile.exists())
    }

    @Test
    fun `a network failure is retried, never treated as a permanent failure`() = runTest {
        val photoId = draftPhotoDao.insert(DraftPhotoEntity(draftId = draftId, localFilePath = tempFile.path))
        queuedSubmissionDao.upsert(
            QueuedSubmissionEntity(draftId = draftId, status = SubmissionStatus.RECEIPTED, receiptId = 4412, enqueuedAtEpochMillis = 0),
        )
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val outcome = useCase().execute(photoId)

        assertEquals(UploadPhotoOutcome.Retry, outcome)
    }

    @Test
    fun `a photo already removed before it uploaded is a harmless no-op`() = runTest {
        val outcome = useCase().execute(photoId = 999L)

        assertEquals(UploadPhotoOutcome.Success, outcome)
    }
}
