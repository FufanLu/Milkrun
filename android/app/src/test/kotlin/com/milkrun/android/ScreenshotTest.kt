package com.milkrun.android

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.milkrun.android.ui.route.RouteScreen
import com.milkrun.android.ui.stop.StopScreen
import com.milkrun.android.ui.theme.MilkrunTheme
import com.milkrun.android.ui.theme.Scheme
import com.milkrun.model.HandoffMethod
import com.milkrun.model.ParcelId
import com.milkrun.model.StopId
import com.milkrun.model.StopStatus
import com.milkrun.presentation.Notice
import com.milkrun.presentation.ParcelRow
import com.milkrun.presentation.RouteUiState
import com.milkrun.presentation.StopCard
import com.milkrun.presentation.StopDetailState
import com.milkrun.presentation.SyncBanner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden screenshots of every state the route and stop screens carry, in all three schemes.
 * `./gradlew :android:app:recordRoborazziDebug` re-records; CI runs `verifyRoborazziDebug`.
 * The data is synthetic (labelled demo addresses), the same shape the shared screen model emits.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one starts WorkManager and a network session, neither wanted here.
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    /** Goldens are recorded on macOS and verified on Linux CI: tolerate anti-aliasing, not layout or colour. */
    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))

    private fun card(
        n: Int,
        address: String,
        status: StopStatus = StopStatus.PLANNED,
        parcels: Int = 1,
        unsynced: Boolean = false,
        afterCancel: Boolean = false,
    ) = StopCard(
        id = StopId("S-%03d".format(n)), sequence = n, number = n.toString().padStart(2, '0'), address = address,
        parcelCount = parcels, scannedCount = if (status == StopStatus.DELIVERED) parcels else 0, status = status,
        attemptReason = null, handoff = if (status == StopStatus.DELIVERED) HandoffMethod.FRONT_DOOR else null,
        unsynced = unsynced, deliveredAfterCancel = afterCancel, note = "",
    )

    private val midShift = RouteUiState(
        routeLabel = "R-2026-11-06-DSE4-017",
        loaded = true,
        remaining = 9,
        delivered = 2,
        attempted = 0,
        sync = SyncBanner.Waiting(pending = 3, retrying = true, lastError = "no signal"),
        next = card(5, "1201 3rd Ave", StopStatus.ARRIVED, unsynced = true),
        upcoming = listOf(card(6, "85 Pike St"), card(7, "300 Pine St", parcels = 3), card(8, "2021 7th Ave")),
        done = listOf(
            card(4, "999 3rd Ave", StopStatus.CANCELLED),
            card(3, "410 Terry Ave N", StopStatus.DELIVERED, parcels = 2, afterCancel = true),
            card(2, "1st Ave S & S Royal Brougham Way", StopStatus.DELIVERED, unsynced = true),
        ),
        notices = listOf(
            Notice(
                7,
                "04",
                "Stop 04 was cancelled by dispatch",
                "Keep its parcels on the van and bring them back to the station.",
            ),
        ),
    )

    private val stop = StopDetailState(
        card = card(5, "1201 3rd Ave", StopStatus.ARRIVED, parcels = 3, unsynced = true),
        parcels = listOf(
            ParcelRow(ParcelId("TBA318840200501"), "padded mailer", scanned = true, delivered = false),
            ParcelRow(ParcelId("TBA318840200502"), "small box", scanned = false, delivered = false),
            ParcelRow(ParcelId("TBA318840200503"), "envelope", scanned = false, delivered = false),
        ),
        canArrive = false,
        canScan = true,
        canDeliver = true,
        canAttempt = true,
    )

    private fun route(scheme: Scheme, name: String) {
        compose.setContent {
            MilkrunTheme(scheme) {
                RouteScreen(midShift, sunMode = scheme == Scheme.SUN, onToggleSun = {
                }, onOpenStop = {}, onArrive = {}, onDismiss = {}, onRefresh = {}, refreshing = false)
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png", roborazziOptions = options)
    }

    private fun stopScreen(scheme: Scheme, name: String) {
        compose.setContent {
            MilkrunTheme(scheme) {
                StopScreen(stop, SnackbarHostState(), onBack = {
                }, onArrive = {}, onScan = {}, onDeliver = {}, onAttempt = {}, onSaveNote = {})
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png", roborazziOptions = options)
    }

    @Test fun routeKraft() = route(Scheme.KRAFT, "route-kraft")

    @Test fun routeNight() = route(Scheme.NIGHT, "route-night")

    @Test fun routeSun() = route(Scheme.SUN, "route-sun")

    @Test fun stopKraft() = stopScreen(Scheme.KRAFT, "stop-kraft")

    @Test fun stopNight() = stopScreen(Scheme.NIGHT, "stop-night")

    @Test
    fun routeSending() {
        compose.setContent {
            MilkrunTheme(Scheme.KRAFT) {
                RouteScreen(
                    midShift.copy(
                        sync = SyncBanner.Waiting(pending = 1, retrying = false, lastError = null),
                        notices = emptyList(),
                    ),
                    sunMode = false,
                    onToggleSun = {
                    },
                    onOpenStop = {},
                    onArrive = {},
                    onDismiss = {},
                    onRefresh = {},
                    refreshing = false,
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/route-sending.png", roborazziOptions = options)
    }

    @Test fun stopSun() = stopScreen(Scheme.SUN, "stop-sun")

    @Test
    fun routeFinished() {
        compose.setContent {
            MilkrunTheme(Scheme.KRAFT) {
                RouteScreen(
                    midShift.copy(
                        remaining = 0,
                        delivered = 11,
                        attempted = 1,
                        next = null,
                        upcoming = emptyList(),
                        notices = emptyList(),
                        sync = SyncBanner.AllSent(1L),
                    ),
                    sunMode = false,
                    onToggleSun = {
                    },
                    onOpenStop = {},
                    onArrive = {},
                    onDismiss = {},
                    onRefresh = {},
                    refreshing = false,
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/route-finished.png", roborazziOptions = options)
    }
}
