package com.example.ui.screens

import android.media.MediaActionSound
import android.net.Uri
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.Observer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.R
import com.example.model.CameraAspectRatio
import com.example.model.CameraFlashMode
import com.example.model.MiniMapSource
import com.example.ui.GeoLensUiState
import com.example.ui.components.WatermarkPreviewCard
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PrecisionAmber
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryTextStyle
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    uiState: GeoLensUiState,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onCycleFlashMode: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onToggleGridLines: () -> Unit,
    onToggleMiniMapSource: () -> Unit,
    onToggleCameraLens: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenInDefaultGallery: (Uri?) -> Unit,
    onPhotoCaptured: (File, Float?) -> Unit,
    onCaptureFailed: (String) -> Unit,
    onClearCaptureMessage: () -> Unit,
    modifier: Modifier = Modifier,
    onCameraReadyChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val perms = uiState.permissionStatus
    val aspectRatioMode = uiState.overlayConfig.aspectRatio

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isCameraStreaming by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    var showShutterFlash by remember { mutableStateOf(false) }

    LaunchedEffect(isCameraStreaming, perms.cameraGranted, cameraError) {
        onCameraReadyChanged(isCameraStreaming || !perms.cameraGranted || cameraError != null)
    }

    val shutterSound = remember {
        MediaActionSound().apply {
            runCatching { load(MediaActionSound.SHUTTER_CLICK) }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { shutterSound.release() }
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val observer = Observer<PreviewView.StreamState> { state ->
            isCameraStreaming = (state == PreviewView.StreamState.STREAMING)
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose {
            previewView.previewStreamState.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.flashMode, imageCapture) {
        imageCapture?.flashMode = when (uiState.flashMode) {
            CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
            CameraFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }
    }

    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            delay(900L)
            focusTapOffset = null
        }
    }

    LaunchedEffect(showShutterFlash) {
        if (showShutterFlash) {
            delay(130L)
            showShutterFlash = false
        }
    }

    LaunchedEffect(uiState.captureStatusMessage) {
        if (uiState.captureStatusMessage != null) {
            delay(3600L)
            onClearCaptureMessage()
        }
    }

    if (perms.cameraGranted) {
        DisposableEffect(lifecycleOwner, uiState.useFrontCamera, aspectRatioMode) {
            isCameraStreaming = false
            val providerFuture = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            var activeProvider: ProcessCameraProvider? = null

            providerFuture.addListener(
                {
                    runCatching {
                        val provider = providerFuture.get()
                        activeProvider = provider
                        val selector = if (uiState.useFrontCamera && provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        val aspectStrategy = when (aspectRatioMode) {
                            CameraAspectRatio.FOUR_THREE -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                            CameraAspectRatio.SIXTEEN_NINE,
                            CameraAspectRatio.FULL -> AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                        }
                        val resolutionSelector = ResolutionSelector.Builder()
                            .setAspectRatioStrategy(aspectStrategy)
                            .build()

                        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

                        val preview = Preview.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setTargetRotation(rotation)
                            .build()
                            .also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setResolutionSelector(resolutionSelector)
                            .setTargetRotation(rotation)
                            .setFlashMode(
                                when (uiState.flashMode) {
                                    CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                                    CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
                                    CameraFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                                }
                            )
                            .build()

                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                        boundCamera = camera
                        imageCapture = capture
                        zoomRatio = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                        cameraError = null
                    }.onFailure { err ->
                        cameraError = err.localizedMessage ?: "Camera preview unavailable on this device"
                    }
                },
                executor,
            )

            onDispose {
                activeProvider?.unbindAll()
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val isLandscape = maxWidth > maxHeight
        val screenPortraitRatio = if (isLandscape) {
            (maxHeight / maxWidth).coerceIn(0.35f, 0.85f)
        } else {
            (maxWidth / maxHeight).coerceIn(0.35f, 0.85f)
        }

        val frameModifier = when (aspectRatioMode) {
            CameraAspectRatio.FOUR_THREE -> {
                val ratio = if (isLandscape) 4f / 3f else 3f / 4f
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .align(Alignment.Center)
            }
            CameraAspectRatio.SIXTEEN_NINE -> {
                val ratio = if (isLandscape) 16f / 9f else 9f / 16f
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .align(Alignment.Center)
            }
            CameraAspectRatio.FULL -> Modifier.fillMaxSize()
        }

        val takePhotoAction = {
            val captureUseCase = imageCapture
            if (captureUseCase != null && !uiState.isCapturing) {
                showShutterFlash = true
                runCatching { shutterSound.play(MediaActionSound.SHUTTER_CLICK) }
                val tempFile = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                captureUseCase.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            onPhotoCaptured(tempFile, screenPortraitRatio)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            tempFile.delete()
                            onCaptureFailed("Capture failed: ${exception.message ?: "Unknown error"}")
                        }
                    },
                )
            }
        }

        // Camera frame (cropped cleanly to 4:3, 16:9, or Full)
        Box(modifier = frameModifier.clipToBounds()) {
            if (perms.cameraGranted && cameraError == null) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(boundCamera) {
                            detectTapGestures { offset ->
                                focusTapOffset = offset
                                val camera = boundCamera ?: return@detectTapGestures
                                val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                                val action = FocusMeteringAction.Builder(point)
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                    .build()
                                camera.cameraControl.startFocusAndMetering(action)
                            }
                        }
                        .pointerInput(boundCamera) {
                            detectTransformGestures { _, _, zoomChange, _ ->
                                val camera = boundCamera ?: return@detectTransformGestures
                                val zoomState = camera.cameraInfo.zoomState.value ?: return@detectTransformGestures
                                val targetZoom = (zoomRatio * zoomChange).coerceIn(
                                    zoomState.minZoomRatio,
                                    zoomState.maxZoomRatio,
                                )
                                zoomRatio = targetZoom
                                camera.cameraControl.setZoomRatio(targetZoom)
                            }
                        },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianVoid),
                )
            }

            // Rule-of-thirds camera gridlines inside the active aspect frame
            if (uiState.overlayConfig.showGridLines) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lineColor = Color.White.copy(alpha = 0.22f)
                    val thirdW = size.width / 3f
                    val thirdH = size.height / 3f
                    drawLine(lineColor, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = 1.2f)
                    drawLine(lineColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = 1.2f)
                    drawLine(lineColor, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = 1.2f)
                    drawLine(lineColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), strokeWidth = 1.2f)
                }
            }

            // Tap-to-focus reticle
            focusTapOffset?.let { tap ->
                val reticleRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (tap.x - reticleRadiusPx).roundToInt(),
                                (tap.y - reticleRadiusPx).roundToInt(),
                            )
                        }
                        .size(56.dp)
                        .border(2.dp, PrecisionAmber, CircleShape),
                )
            }

            // Camera startup buffering indicator until PreviewView starts streaming frames
            AnimatedVisibility(
                visible = perms.cameraGranted && cameraError == null && !isCameraStreaming,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Surface(
                    color = Color(0xCC141619),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier.testTag("camera_loading_indicator"),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            color = PrecisionAmber,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = stringResource(R.string.camera_loading),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                }
            }

            // For non-Full ratios (4:3, 16:9), show live Geotag overlay right inside the bottom of the frame!
            if (aspectRatioMode != CameraAspectRatio.FULL) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    WatermarkPreviewCard(
                        locationFix = uiState.locationFix,
                        compassState = uiState.compassState,
                        addressState = uiState.addressState,
                        overlayConfig = uiState.overlayConfig,
                        currentTimeMillis = uiState.currentTimeMillis,
                        modifier = Modifier.testTag("watermark_preview_card"),
                    )
                }
            }
        }

        // Brief shutter flash effect
        AnimatedVisibility(
            visible = showShutterFlash,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.55f)),
            )
        }

        // Floating top camera controls bar
        TopCameraControlsBar(
            uiState = uiState,
            zoomRatio = zoomRatio,
            onCycleFlashMode = onCycleFlashMode,
            onCycleAspectRatio = onCycleAspectRatio,
            onToggleGridLines = onToggleGridLines,
            onToggleMiniMapSource = onToggleMiniMapSource,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )

        // Centered permission / camera error prompt when needed (single Enable Camera & GPS button)
        if (!perms.cameraGranted || !perms.legacyStorageGranted || cameraError != null) {
            CameraPermissionOverlayPrompt(
                cameraGranted = perms.cameraGranted,
                hasLocationPermission = perms.hasAnyLocationPermission,
                legacyStorageGranted = perms.legacyStorageGranted,
                cameraError = cameraError,
                onRequestPermissions = onRequestPermissions,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
            )
        }

        // Capture feedback pill with one-tap "Open" in Default Gallery
        AnimatedVisibility(
            visible = uiState.captureStatusMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp),
        ) {
            Surface(
                color = Color(0xE0141619),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.75f)),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = uiState.captureStatusMessage.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                    if (uiState.lastCapturedUri != null) {
                        Surface(
                            color = PrecisionAmber,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .clickable { onOpenInDefaultGallery(uiState.lastCapturedUri) }
                                .testTag("open_saved_photo_pill"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInNew,
                                    contentDescription = stringResource(R.string.open_in_gallery),
                                    tint = ObsidianVoid,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "Open",
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                                    color = ObsidianVoid,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom stack: Geotag overlay (when Full screen) + Shutter bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (perms.hasAnyLocationPermission && !perms.locationServiceEnabled) {
                Surface(
                    color = Color(0xCC7F1D1D),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, SignalCrimson),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clickable(onClick = onOpenLocationSettings)
                        .testTag("location_disabled_pill"),
                ) {
                    Text(
                        text = "Device GPS is off • Tap to enable",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }

            if (aspectRatioMode == CameraAspectRatio.FULL) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                ) {
                    WatermarkPreviewCard(
                        locationFix = uiState.locationFix,
                        compassState = uiState.compassState,
                        addressState = uiState.addressState,
                        overlayConfig = uiState.overlayConfig,
                        currentTimeMillis = uiState.currentTimeMillis,
                        modifier = Modifier.testTag("watermark_preview_card"),
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            CameraShutterBar(
                canCapture = perms.cameraGranted && perms.legacyStorageGranted && imageCapture != null && cameraError == null,
                isCapturing = uiState.isCapturing,
                lastCapturedUri = uiState.lastCapturedUri?.toString(),
                onOpenGallery = onOpenGallery,
                onShutterClick = takePhotoAction,
                onFlipCamera = onToggleCameraLens,
            )
        }
    }
}

@Composable
private fun TopCameraControlsBar(
    uiState: GeoLensUiState,
    zoomRatio: Float,
    onCycleFlashMode: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onToggleGridLines: () -> Unit,
    onToggleMiniMapSource: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOnlineSatellite = uiState.overlayConfig.miniMapSource == MiniMapSource.GOOGLE_LIVE_SATELLITE
    val mapAccentColor = if (isOnlineSatellite) SignalEmerald else PrecisionAmber

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = Color(0x99141619),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            ) {
                IconButton(
                    onClick = onCycleFlashMode,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("flash_button"),
                ) {
                    Icon(
                        imageVector = when (uiState.flashMode) {
                            CameraFlashMode.AUTO -> Icons.Rounded.FlashAuto
                            CameraFlashMode.ON -> Icons.Rounded.FlashOn
                            CameraFlashMode.OFF -> Icons.Rounded.FlashOff
                        },
                        contentDescription = stringResource(R.string.cd_flash_button),
                        tint = if (uiState.flashMode == CameraFlashMode.ON) PrecisionAmber else Color.White,
                    )
                }
            }

            // Aspect ratio selector pill (4:3, 16:9, Full)
            Surface(
                color = Color(0x99141619),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, PrecisionAmber.copy(alpha = 0.5f)),
                modifier = Modifier
                    .clickable(onClick = onCycleAspectRatio)
                    .minimumInteractiveComponentSize()
                    .semantics { contentDescription = "Aspect ratio ${uiState.overlayConfig.aspectRatio.label}" }
                    .testTag("aspect_ratio_button"),
            ) {
                Text(
                    text = uiState.overlayConfig.aspectRatio.label,
                    style = TelemetryTextStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrecisionAmber,
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

            Surface(
                color = Color(0x99141619),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            ) {
                IconButton(
                    onClick = onToggleGridLines,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("grid_button"),
                ) {
                    Icon(
                        imageVector = if (uiState.overlayConfig.showGridLines) {
                            Icons.Rounded.GridOn
                        } else {
                            Icons.Rounded.GridOff
                        },
                        contentDescription = stringResource(R.string.cd_grid_button),
                        tint = if (uiState.overlayConfig.showGridLines) PrecisionAmber else Color.White,
                    )
                }
            }
        }

        // Online / Offline Satellite Map toggle pill
        Surface(
            color = Color(0xB3141619),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, mapAccentColor.copy(alpha = 0.7f)),
            modifier = Modifier
                .clickable(onClick = onToggleMiniMapSource)
                .minimumInteractiveComponentSize()
                .semantics {
                    contentDescription = if (isOnlineSatellite) {
                        "Online satellite map active, tap to switch to offline"
                    } else {
                        "Offline satellite map active, tap to switch to online"
                    }
                }
                .testTag("map_mode_toggle_chip"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = if (isOnlineSatellite) Icons.Rounded.Public else Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = mapAccentColor,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = buildString {
                        append(if (isOnlineSatellite) "Online Map" else "Offline Map")
                        if (zoomRatio > 1.05f) {
                            append(" • ${String.format(java.util.Locale.US, "%.1fx", zoomRatio)}")
                        }
                    },
                    style = TelemetryTextStyle.copy(fontSize = 11.sp, color = Color.White),
                )
            }
        }

        // Overlay studio / settings icon
        Surface(
            color = Color(0x99141619),
            shape = CircleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("header_settings_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.cd_settings_button),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CameraShutterBar(
    canCapture: Boolean,
    isCapturing: Boolean,
    lastCapturedUri: String?,
    onOpenGallery: () -> Unit,
    onShutterClick: () -> Unit,
    onFlipCamera: () -> Unit,
) {
    val shutterScale by animateFloatAsState(
        targetValue = if (isCapturing) 0.88f else 1f,
        label = "shutterScale",
    )
    val shutterDesc = stringResource(R.string.cd_shutter_button)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Color(0xB3141619),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)),
            modifier = Modifier
                .size(52.dp)
                .clickable(onClick = onOpenGallery)
                .testTag("gallery_button"),
        ) {
            if (lastCapturedUri != null) {
                AsyncImage(
                    model = lastCapturedUri,
                    contentDescription = stringResource(R.string.cd_gallery_button),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = stringResource(R.string.cd_gallery_button),
                        tint = Color.White,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(shutterScale)
                .clip(CircleShape)
                .border(4.dp, Color.White, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(if (canCapture) Color.White else Color.White.copy(alpha = 0.35f))
                .clickable(enabled = canCapture && !isCapturing, onClick = onShutterClick)
                .semantics { contentDescription = shutterDesc }
                .testTag("shutter_button"),
            contentAlignment = Alignment.Center,
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = ObsidianVoid,
                    strokeWidth = 3.dp,
                )
            }
        }

        Surface(
            color = Color(0xB3141619),
            shape = CircleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            modifier = Modifier.size(52.dp),
        ) {
            IconButton(
                onClick = onFlipCamera,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("flip_camera_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FlipCameraAndroid,
                    contentDescription = stringResource(R.string.cd_switch_camera),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CameraPermissionOverlayPrompt(
    cameraGranted: Boolean,
    hasLocationPermission: Boolean,
    legacyStorageGranted: Boolean,
    cameraError: String?,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xE014171D),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PrecisionAmber.copy(alpha = 0.5f)),
        modifier = modifier.testTag("permission_rationale_card"),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                tint = PrecisionAmber,
                modifier = Modifier.size(34.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (cameraError != null) "Camera Unavailable" else stringResource(R.string.permission_banner_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(6.dp))
            val missingParts = buildList {
                if (!cameraGranted) add("Camera")
                if (!hasLocationPermission) add("Location")
                if (!legacyStorageGranted) add("Storage")
            }
            Text(
                text = cameraError ?: buildString {
                    append(stringResource(R.string.permission_banner_body))
                    if (missingParts.isNotEmpty()) {
                        append(" (Needs: ${missingParts.joinToString(", ")})")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1),
            )
            if (cameraError == null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrecisionAmber,
                        contentColor = ObsidianVoid,
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("grant_permissions_button"),
                ) {
                    Text(stringResource(R.string.permission_grant_action))
                }
            }
        }
    }
}
