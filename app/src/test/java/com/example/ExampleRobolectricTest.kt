package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.location.CoordinateFormatter
import com.example.location.PlusCodeEncoder
import com.example.model.AddressState
import com.example.model.CoordinateFormat
import com.example.model.FixQuality
import com.example.model.LocationFix
import com.example.model.MiniMapSource
import com.example.model.OverlayConfig
import com.example.model.PermissionStatus
import com.example.ui.GeoLensUiState
import com.example.ui.components.RoletCamSplashOverlay
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.OverlaySettingsScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Rolet Cam", appName)
    }

    @Test
    fun `camera screen shows online offline map toggle, shutter controls, Captured by Rolet Cam badge, and single permission button`() {
        val sampleFix = LocationFix(
            latitude = 22.572600,
            longitude = 88.363900,
            altitudeMeters = 11.0,
            accuracyMeters = 5.0f,
            speedMetersPerSecond = 0f,
            bearingDegrees = 45f,
            timestampMillis = 1700000000000L,
            provider = "gps",
            fixQuality = FixQuality.HIGH_PRECISION,
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                CameraScreen(
                    uiState = GeoLensUiState(
                        permissionStatus = PermissionStatus(
                            cameraGranted = false,
                            fineLocationGranted = true,
                            coarseLocationGranted = true,
                            hasRequestedPermissions = true,
                        ),
                        locationFix = sampleFix,
                        addressState = AddressState.Resolved(
                            shortLine = "College Square, Kolkata, West Bengal",
                            fullAddress = "College Square, Kolkata, West Bengal, 700073",
                            locality = "Kolkata",
                            country = "India",
                        ),
                    ),
                    onRequestPermissions = {},
                    onOpenLocationSettings = {},
                    onCycleFlashMode = {},
                    onCycleAspectRatio = {},
                    onToggleGridLines = {},
                    onToggleMiniMapSource = {},
                    onToggleCameraLens = {},
                    onOpenSettings = {},
                    onOpenGallery = {},
                    onOpenInDefaultGallery = {},
                    onPhotoCaptured = { _, _ -> },
                    onCaptureFailed = {},
                    onClearCaptureMessage = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("aspect_ratio_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_mode_toggle_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gps_status_chip").assertDoesNotExist()
        composeTestRule.onNodeWithText("Offline Map").assertIsDisplayed()
        composeTestRule.onNodeWithTag("permission_rationale_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grant_permissions_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_app_settings_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("watermark_preview_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Captured by Rolet Cam").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shutter_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("flip_camera_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lat 22.572600°   Long 88.363900°").assertIsDisplayed()

        assertEquals("7MJCH9F7+2H", PlusCodeEncoder.encode(22.572600, 88.363900))
        assertEquals(
            "22.572600° N",
            CoordinateFormatter.formatLatitude(sampleFix.latitude, CoordinateFormat.DECIMAL_DEGREES),
        )
    }

    @Test
    fun `startup splash overlay renders Rolet Cam brand logo and loading state`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                RoletCamSplashOverlay()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("startup_splash_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rolet Cam").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading camera & GPS…").assertIsDisplayed()
    }

    @Test
    fun `settings screen allows switching between offline and google live satellite mini-map via radio buttons`() {
        var selectedSource = MiniMapSource.OFFLINE_SATELLITE
        composeTestRule.setContent {
            MyApplicationTheme {
                OverlaySettingsScreen(
                    uiState = GeoLensUiState(
                        overlayConfig = OverlayConfig(miniMapSource = selectedSource),
                    ),
                    onBack = {},
                    onCoordinateFormatChange = {},
                    onUnitSystemChange = {},
                    onWatermarkStyleChange = {},
                    onAspectRatioChange = {},
                    onMiniMapSourceChange = { selectedSource = it },
                    onToggleChange = { _, _, _, _, _, _, _, _, _ -> },
                    onCustomNoteChange = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("minimap_radio_OFFLINE_SATELLITE").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithTag("minimap_radio_GOOGLE_LIVE_SATELLITE").performScrollTo().performClick()
        assertEquals(MiniMapSource.GOOGLE_LIVE_SATELLITE, selectedSource)
    }
}
