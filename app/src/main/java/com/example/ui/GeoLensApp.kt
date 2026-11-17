package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.RoletCamSplashOverlay
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.OverlaySettingsScreen
import kotlinx.coroutines.delay

const val ROUTE_CAMERA = "camera"
const val ROUTE_GALLERY = "gallery"
const val ROUTE_OVERLAY_SETTINGS = "overlay_settings"

@Composable
fun GeoLensApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.markPermissionsRequested()
        viewModel.refreshPermissionStatus(context)
    }

    val requestCorePermissions = {
        val perms = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionStatus(context)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissionStatus(context)
        viewModel.refreshGallery()
    }

    val openLocationSettings = {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    val openInDefaultGallery: (Uri?) -> Unit = { uri ->
        val viewIntent = if (uri != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
        runCatching {
            context.startActivity(viewIntent)
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                )
            }
        }
    }

    var showStartupSplash by remember { mutableStateOf(true) }
    var isCameraReady by remember { mutableStateOf(false) }
    var minSplashElapsed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(950L)
        minSplashElapsed = true
    }

    LaunchedEffect(Unit) {
        delay(3200L)
        showStartupSplash = false
    }

    LaunchedEffect(minSplashElapsed, isCameraReady) {
        if (minSplashElapsed && isCameraReady) {
            showStartupSplash = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_CAMERA,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(ROUTE_CAMERA) {
                CameraScreen(
                    uiState = uiState,
                    onRequestPermissions = requestCorePermissions,
                    onOpenLocationSettings = openLocationSettings,
                    onCycleFlashMode = viewModel::cycleFlashMode,
                    onCycleAspectRatio = viewModel::cycleAspectRatio,
                    onToggleGridLines = viewModel::toggleGridLines,
                    onToggleMiniMapSource = viewModel::toggleMiniMapSource,
                    onToggleCameraLens = viewModel::toggleFrontBackCamera,
                    onOpenSettings = { navController.navigate(ROUTE_OVERLAY_SETTINGS) },
                    onOpenGallery = {
                        viewModel.refreshGallery()
                        navController.navigate(ROUTE_GALLERY)
                    },
                    onOpenInDefaultGallery = openInDefaultGallery,
                    onPhotoCaptured = { file, fullAspect ->
                        viewModel.onRawPhotoCaptured(file, fullAspect)
                    },
                    onCaptureFailed = viewModel::onCaptureFailed,
                    onClearCaptureMessage = viewModel::clearCaptureMessage,
                    onCameraReadyChanged = { ready -> isCameraReady = ready },
                )
            }

            composable(ROUTE_GALLERY) {
                GalleryScreen(
                    photos = uiState.capturedPhotos,
                    onBack = { navController.popBackStack() },
                    onOpenInDefaultGallery = { uri -> openInDefaultGallery(uri) },
                    onLoadExif = { uri -> viewModel.loadExifSummary(uri) },
                    onDeletePhoto = viewModel::deletePhoto,
                )
            }

            composable(ROUTE_OVERLAY_SETTINGS) {
                OverlaySettingsScreen(
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onCoordinateFormatChange = viewModel::setCoordinateFormat,
                    onUnitSystemChange = viewModel::setUnitSystem,
                    onWatermarkStyleChange = viewModel::setWatermarkStyle,
                    onAspectRatioChange = viewModel::setAspectRatio,
                    onMiniMapSourceChange = viewModel::setMiniMapSource,
                    onToggleChange = { addr, coord, elev, comp, time, acc, map, grid, raw ->
                        viewModel.updateOverlayToggle(
                            showAddress = addr,
                            showCoordinates = coord,
                            showElevation = elev,
                            showCompass = comp,
                            showTimestamp = time,
                            showAccuracy = acc,
                            showMiniMap = map,
                            showGridLines = grid,
                            saveOriginalCopy = raw,
                        )
                    },
                    onCustomNoteChange = viewModel::setCustomNote,
                )
            }
        }

        AnimatedVisibility(
            visible = showStartupSplash,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(durationMillis = 420)),
        ) {
            RoletCamSplashOverlay()
        }
    }
}
