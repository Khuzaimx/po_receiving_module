package com.sellernest.poreceiving.ui.components

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sellernest.poreceiving.MainActivity
import com.sellernest.poreceiving.data.local.dao.QueuedSubmissionDao
import com.sellernest.poreceiving.data.local.entities.QueuedSubmissionEntity
import com.sellernest.poreceiving.data.local.entities.SubmissionStatus
import com.sellernest.poreceiving.navigation.PoReceivingRoot
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M0.5 acceptance criterion: "Enqueuing a submission increments the visible
 * pending count." Reaches the real app's Hilt-provided [QueuedSubmissionDao] via
 * an [EntryPoint] rather than the full `@HiltAndroidTest` scaffolding, since this
 * test only reads the existing DI graph and never overrides a binding.
 *
 * Runs against the app's real database (there is no test double wired in), so it
 * uses a `draftId` far outside any real range and tears itself down by marking
 * the row RECEIPTED afterward -- there is deliberately no delete method on
 * [QueuedSubmissionDao] (queued submissions, like drafts, are meant to persist as
 * an audit trail, never be purged), so cleanup works the same way production code
 * would retire one, not by removing the row.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface QueuedSubmissionDaoEntryPoint {
    fun queuedSubmissionDao(): QueuedSubmissionDao
}

@RunWith(AndroidJUnit4::class)
class StatusBarPendingCountInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val testDraftId = 999_999_999L

    private val dao: QueuedSubmissionDao by lazy {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        EntryPointAccessors.fromApplication(appContext, QueuedSubmissionDaoEntryPoint::class.java)
            .queuedSubmissionDao()
    }

    @After
    fun tearDown() = runTest {
        dao.getForDraft(testDraftId)?.let { existing ->
            dao.update(existing.copy(status = SubmissionStatus.RECEIPTED))
        }
    }

    @Test
    fun enqueuingASubmissionIncrementsTheVisiblePendingCount() = runTest {
        // Ensure a leftover row from a previous run (if any) doesn't count as pending.
        dao.getForDraft(testDraftId)?.let { existing ->
            dao.update(existing.copy(status = SubmissionStatus.RECEIPTED))
        }

        composeTestRule.setContent {
            PoReceivingRoot()
        }
        composeTestRule.waitForIdle()

        // The badge is absent entirely when nothing is queued, not present-but-zero.
        composeTestRule.onNodeWithTag("status_bar_pending_count").assertDoesNotExist()

        dao.upsert(
            QueuedSubmissionEntity(
                draftId = testDraftId,
                status = SubmissionStatus.PENDING,
                enqueuedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("status_bar_pending_count").assertExists()
    }
}
