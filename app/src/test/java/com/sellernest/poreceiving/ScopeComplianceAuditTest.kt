package com.sellernest.poreceiving

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.4: "A final pass proving the shipped app matches the spec's deliberate
 * exclusions. These are decisions, not oversights, and must not be added
 * opportunistically." Every check below is a source-level grep across the
 * whole `main` source set -- run from the repo, not against any one file --
 * since the point of this audit is that a listed API/pattern is *absent
 * everywhere*, not merely unused on one screen.
 *
 * The §11 functional re-check bullets are not repeated here as fresh
 * assertions -- each already has a dedicated positive test elsewhere in this
 * codebase; duplicating them would just be two places that can drift apart:
 *
 * | §11 functional re-check | Covered by |
 * |---|---|
 * | Sign in via Custom Tab; tokens encrypted; silent refresh; failed refresh preserves drafts | M1.2-M1.4's auth tests (pre-existing); `SessionInvalidationNotifier`/`PoReceivingRoot`'s hard-logout path never touches Room |
 * | Work queue lists only permitted-warehouse POs; scanned barcode opens directly | `WorkQueueViewModelTest`'s warehouse-scoping and PO-barcode-scan tests |
 * | Every scan resolved server-side, all four outcomes handled distinctly | `ScanResolutionSerializer`'s exhaustive `when`; `ScanOutcomeBanner`; `ScanToCountViewModelTest` |
 * | No expected quantity displayed or held on device before commit -- verified via the network payload, not just the UI | `BlindCountDtoTest`/`DraftLineEntityBlindCountTest` (reflection over the actual DTO/entity fields, not screen-reading) |
 * | Variances require a reason; damage visibly separated from good; photos attach to a variance | `ReconcileViewModelTest`, `DamageCaptureViewModelTest` |
 * | Serial capture: exact count, duplicate rejection | `SerialCaptureViewModelTest` |
 * | Bin scan mandatory where enforced | `BinConfirmationViewModelTest`, `ReconcileViewModelTest`'s bin-routing tests |
 * | Submission queued, retried with the same idempotency key, partial failure per line | `SubmitDraftUseCaseTest` |
 * | Receipt history: own receipts; void works in-window and explains itself outside it | `ReceiptHistoryViewModelTest` |
 */
class ScopeComplianceAuditTest {

    private val mainSource: String by lazy { readAllMainSource() }

    @Test
    fun noOutOfScopeFeatureExistsAnywhere() {
        // §1.3/§12: explicitly out of scope, and not merely deferred --
        // these must never appear in the shipped architecture at all.
        val forbidden = listOf(
            "Picking", "Packing", "Shipment", "CycleCount", "Transfer",
            "Chargeback", "VendorClaim", "LicensePlate", "PalletId", "CrossDock",
            "RmaReceiving", "LabelPrint", "SignUp", "PasswordReset", "ForgotPassword",
            "EditPermission", "EditProfile",
        )
        val present = forbidden.filter { mainSource.contains(it) }
        assertTrue("Found source referencing an explicitly out-of-scope feature: $present", present.isEmpty())
    }

    @Test
    fun nothingIsBuiltForIosOrAnyCrossPlatformFramework() {
        // §1.3: this is deliberately a single-platform, native Android app.
        val repoRoot = repoRoot()
        val crossPlatformFiles = repoRoot.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                file.extension in setOf("pbxproj") ||
                    file.name in setOf("Podfile", "pubspec.yaml", "project.pbxproj")
            }
            .map { it.path }
            .toList()
        assertTrue("Found what looks like an iOS/cross-platform project file: $crossPlatformFiles", crossPlatformFiles.isEmpty())
    }

    @Test
    fun noSwipeToDismissExistsOnAnyList() {
        // §3.1: "No swipe-to-delete on any list" -- checked against the
        // Compose APIs that would implement it, not just the word "swipe".
        val forbidden = listOf("SwipeToDismiss", "rememberSwipeToDismissBoxState", "DismissDirection")
        val present = forbidden.filter { mainSource.contains(it) }
        assertTrue("Found a swipe-to-dismiss API in use: $present", present.isEmpty())
    }

    @Test
    fun noBottomSheetExistsAnywhere() {
        // §3.1: "No bottom sheets for critical actions" -- this app has no
        // bottom sheet at all, critical or otherwise.
        val forbidden = listOf("ModalBottomSheet", "BottomSheetScaffold", "rememberModalBottomSheetState")
        val present = forbidden.filter { mainSource.contains(it) }
        assertTrue("Found a bottom sheet API in use: $present", present.isEmpty())
    }

    @Test
    fun noSnackbarExistsAnywhere() {
        // §3.1: "No snackbar as the sole confirmation of a state change" --
        // this app doesn't use one at all, so the "sole" qualifier can't be
        // violated by construction; every confirmation is a persistent
        // StateBadge or an AlertDialog the receiver must dismiss.
        val forbidden = listOf("Snackbar", "SnackbarHost", "SnackbarDuration")
        val present = forbidden.filter { mainSource.contains(it) }
        assertTrue("Found a Snackbar API in use: $present", present.isEmpty())
    }

    @Test
    fun everyPullToRefreshIsPairedWithAnExplicitRefreshControl() {
        // §3.1: "No pull-to-refresh as the sole refresh mechanism." Every
        // screen file using PullToRefreshBox/SwipeRefresh must also contain
        // an explicit refresh affordance (an IconButton wired to a refresh
        // event) in the same file.
        val screenFiles = mainSourceRoot().walkTopDown().filter { it.extension == "kt" }
        val offenders = screenFiles.filter { file ->
            val text = file.readText()
            val hasPullToRefresh = text.contains("PullToRefreshBox") || text.contains("SwipeRefresh")
            hasPullToRefresh && !text.contains("IconButton")
        }.map { it.name }
        assertTrue("Found pull-to-refresh with no adjacent explicit refresh control: $offenders", offenders.isEmpty())
    }

    @Test
    fun noDarkThemeIsDefined() {
        // §3.1: "No dark mode in this release." Theme.kt's own doc explains
        // the omission; this confirms no *other* file defines one instead.
        val forbidden = listOf("darkColorScheme(", "isSystemInDarkTheme(")
        val offendingFiles = mainSourceRoot().walkTopDown()
            .filter { it.extension == "kt" }
            .filter { file -> forbidden.any { file.readText().contains(it) } }
            .map { it.name }
            .toList()
        assertTrue("Found a dark-theme definition outside Theme.kt's documented absence: $offendingFiles", offendingFiles.isEmpty())
    }

    @Test
    fun noDecorativeMotionApiIsUsed() {
        // §3.1: "No decorative motion, parallax or shared-element
        // transitions." Ordinary state-driven Compose recomposition (an
        // AlertDialog appearing, a list updating) isn't "motion" in the
        // sense this anti-requirement means -- the APIs that would add
        // gratuitous animation are checked for directly instead.
        val forbidden = listOf("SharedTransitionLayout", "sharedElement(", "Parallax", "AnimatedContent(", "Crossfade(")
        val present = forbidden.filter { mainSource.contains(it) }
        assertTrue("Found a decorative-motion API in use: $present", present.isEmpty())
    }

    @Test
    fun noTwoHandedGestureApiIsUsed() {
        // §3.1: "No screen whose primary action requires two hands." There
        // is no drag/multi-touch gesture code anywhere for a primary action
        // to hide behind -- every primary action is PrimaryButton, a single
        // full-width tap target.
        val forbidden = listOf("detectDragGestures", "Modifier.draggable", "detectTransformGestures", "rememberTransformableState")
        val present = forbidden.filter { mainSource.contains(it) }
        assertTrue("Found a drag/multi-touch gesture API in use: $present", present.isEmpty())
    }

    private fun readAllMainSource(): String =
        mainSourceRoot().walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }

    private fun mainSourceRoot(): File {
        val candidates = listOf(File("src/main/java"), File("app/src/main/java"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate the main source root from working dir ${File(".").absolutePath}")
    }

    /**
     * Walks up from the working directory looking for `settings.gradle.kts`
     * rather than a fixed number of parent hops: [File.getParentFile] returns
     * null one level too early for a bare relative path like `File("src")`
     * (no `..`/`.` segment to derive a parent from), so a fixed hop count
     * silently under-scans depending on whether tests run from the repo root
     * or the `app/` module directory.
     */
    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        return dir
    }
}
