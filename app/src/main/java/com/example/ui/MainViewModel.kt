package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.location.CompassSensorManager
import com.example.location.LocationRepository
import com.example.location.OfflineGeocoder
import com.example.model.AddressState
import com.example.model.CameraAspectRatio
import com.example.model.CameraFlashMode
import com.example.model.CapturedPhoto
import com.example.model.CompassState
import com.example.model.CoordinateFormat
import com.example.model.ExifSummary
import com.example.model.LocationFix
import com.example.model.MiniMapSource
import com.example.model.OverlayConfig
import com.example.model.PermissionStatus
import com.example.model.UnitSystem
import com.example.model.WatermarkStyle
import com.example.settings.OverlayPreferencesRepository
import com.example.storage.MediaStorePhotoRepository
import com.example.storage.PhotoOverlayProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class GeoLensUiState(
    val permissionStatus: PermissionStatus = PermissionStatus(),
    val locationFix: LocationFix? = null,
    val compassState: CompassState = CompassState(),
    val addressState: AddressState = AddressState.Idle,
    val overlayConfig: OverlayConfig = OverlayConfig(),
    val currentTimeMillis: Long = System.currentTimeMillis(),
    val flashMode: CameraFlashMode = CameraFlashMode.AUTO,
    val useFrontCamera: Boolean = false,
    val isCapturing: Boolean = false,
    val lastCapturedUri: Uri? = null,
    val captureStatusMessage: String? = null,
    val capturedPhotos: List<CapturedPhoto> = emptyList(),
)

class MainViewModel(
    private val locationRepository: LocationRepository,
    private val compassSensorManager: CompassSensorManager,
    private val offlineGeocoder: OfflineGeocoder,
    private val preferencesRepository: OverlayPreferencesRepository,
    private val mediaStorePhotoRepository: MediaStorePhotoRepository,
    private val photoOverlayProcessor: PhotoOverlayProcessor,
) : ViewModel() {

    private val permissionFlow = MutableStateFlow(PermissionStatus())
    private val locationFixFlow = MutableStateFlow<LocationFix?>(null)
    private val compassFlow = MutableStateFlow(CompassState())
    private val addressFlow = MutableStateFlow<AddressState>(AddressState.Idle)
    private val clockFlow = MutableStateFlow(System.currentTimeMillis())
    private val cameraControlFlow = MutableStateFlow(CameraControlState())
    private val photosFlow = MutableStateFlow<List<CapturedPhoto>>(emptyList())

    private var locationJob: Job? = null
    private var geocodeJob: Job? = null

    private data class CameraControlState(
        val flashMode: CameraFlashMode = CameraFlashMode.AUTO,
        val useFrontCamera: Boolean = false,
        val isCapturing: Boolean = false,
        val lastCapturedUri: Uri? = null,
        val captureStatusMessage: String? = null,
    )

    val uiState: StateFlow<GeoLensUiState> = combine(
        permissionFlow,
        locationFixFlow,
        compassFlow,
        addressFlow,
        preferencesRepository.overlayConfigFlow,
        clockFlow,
        cameraControlFlow,
        photosFlow,
    ) { values ->
        val perms = values[0] as PermissionStatus
        val fix = values[1] as LocationFix?
        val compass = values[2] as CompassState
        val address = values[3] as AddressState
        val config = values[4] as OverlayConfig
        val now = values[5] as Long
        val cam = values[6] as CameraControlState
        @Suppress("UNCHECKED_CAST")
        val photos = values[7] as List<CapturedPhoto>

        GeoLensUiState(
            permissionStatus = perms,
            locationFix = fix,
            compassState = compass,
            addressState = address,
            overlayConfig = config,
            currentTimeMillis = now,
            flashMode = cam.flashMode,
            useFrontCamera = cam.useFrontCamera,
            isCapturing = cam.isCapturing,
            lastCapturedUri = cam.lastCapturedUri ?: photos.firstOrNull()?.uri,
            captureStatusMessage = cam.captureStatusMessage,
            capturedPhotos = photos,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = GeoLensUiState(),
    )

    init {
        viewModelScope.launch {
            compassSensorManager.observeCompass().collect { state ->
                compassFlow.value = state
            }
        }
        viewModelScope.launch {
            preferencesRepository.hasRequestedPermissionsFlow.collect { requested ->
                permissionFlow.value = permissionFlow.value.copy(hasRequestedPermissions = requested)
            }
        }
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                clockFlow.value = now
                val delayToNextMinute = (60_000L - (now % 60_000L)).coerceIn(1_000L, 60_000L)
                delay(delayToNextMinute)
            }
        }
        refreshGallery()
    }

    fun refreshPermissionStatus(context: Context) {
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val legacyStorageGranted = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val serviceEnabled = locationRepository.isLocationServiceEnabled()

        val previous = permissionFlow.value
        val updated = previous.copy(
            cameraGranted = cameraGranted,
            fineLocationGranted = fineGranted,
            coarseLocationGranted = coarseGranted,
            legacyStorageGranted = legacyStorageGranted,
            locationServiceEnabled = serviceEnabled,
        )
        permissionFlow.value = updated

        val locationAccessChanged =
            previous.hasAnyLocationPermission != updated.hasAnyLocationPermission ||
                previous.fineLocationGranted != updated.fineLocationGranted ||
                previous.locationServiceEnabled != updated.locationServiceEnabled

        if (updated.hasAnyLocationPermission && updated.locationServiceEnabled) {
            if (locationJob == null || locationAccessChanged) {
                startLocationUpdates(fineGranted = updated.fineLocationGranted)
            }
        } else {
            stopLocationUpdates()
        }
    }

    fun markPermissionsRequested() {
        viewModelScope.launch {
            preferencesRepository.markPermissionsRequested()
        }
    }

    fun forceRestartLocationUpdates() {
        val perms = permissionFlow.value
        if (perms.hasAnyLocationPermission && perms.locationServiceEnabled) {
            startLocationUpdates(fineGranted = perms.fineLocationGranted)
        }
    }

    fun cycleFlashMode() {
        val next = when (cameraControlFlow.value.flashMode) {
            CameraFlashMode.AUTO -> CameraFlashMode.ON
            CameraFlashMode.ON -> CameraFlashMode.OFF
            CameraFlashMode.OFF -> CameraFlashMode.AUTO
        }
        cameraControlFlow.value = cameraControlFlow.value.copy(flashMode = next)
    }

    fun cycleAspectRatio() {
        val next = when (uiState.value.overlayConfig.aspectRatio) {
            CameraAspectRatio.FOUR_THREE -> CameraAspectRatio.SIXTEEN_NINE
            CameraAspectRatio.SIXTEEN_NINE -> CameraAspectRatio.FULL
            CameraAspectRatio.FULL -> CameraAspectRatio.FOUR_THREE
        }
        setAspectRatio(next)
    }

    fun setAspectRatio(aspectRatio: CameraAspectRatio) {
        viewModelScope.launch { preferencesRepository.setAspectRatio(aspectRatio) }
    }

    fun toggleFrontBackCamera() {
        cameraControlFlow.value = cameraControlFlow.value.copy(
            useFrontCamera = !cameraControlFlow.value.useFrontCamera,
        )
    }

    fun toggleGridLines() {
        val next = !uiState.value.overlayConfig.showGridLines
        updateOverlayToggle(showGridLines = next)
    }

    fun onRawPhotoCaptured(tempFile: File, fullScreenAspectRatio: Float? = null) {
        viewModelScope.launch {
            cameraControlFlow.value = cameraControlFlow.value.copy(isCapturing = true)
            val snapshotFix = locationFixFlow.value
            val snapshotCompass = compassFlow.value
            val snapshotAddress = addressFlow.value
            val snapshotConfig = uiState.value.overlayConfig
            val captureTime = System.currentTimeMillis()

            val result = photoOverlayProcessor.processAndSavePhoto(
                sourceJpegFile = tempFile,
                locationFix = snapshotFix,
                compassState = snapshotCompass,
                addressState = snapshotAddress,
                overlayConfig = snapshotConfig,
                captureTimestampMillis = captureTime,
                fullScreenAspectRatio = fullScreenAspectRatio,
            )

            val savedUri = result.getOrNull()
            val error = result.exceptionOrNull()
            if (error != null) {
                Log.e("GeoLensCapture", "Failed to save photo", error)
            }

            val message = when {
                savedUri != null && snapshotFix != null -> "Saved to Camera Gallery"
                savedUri != null -> "Saved to Gallery (No GPS fix)"
                else -> "Save failed: ${error?.message ?: "Storage error"}"
            }

            cameraControlFlow.value = cameraControlFlow.value.copy(
                isCapturing = false,
                lastCapturedUri = savedUri ?: cameraControlFlow.value.lastCapturedUri,
                captureStatusMessage = message,
            )
            refreshGallery()
        }
    }

    fun onCaptureFailed(reason: String) {
        cameraControlFlow.value = cameraControlFlow.value.copy(
            isCapturing = false,
            captureStatusMessage = reason,
        )
    }

    fun clearCaptureMessage() {
        cameraControlFlow.value = cameraControlFlow.value.copy(captureStatusMessage = null)
    }

    fun refreshGallery() {
        viewModelScope.launch {
            photosFlow.value = mediaStorePhotoRepository.queryCapturedPhotos()
        }
    }

    suspend fun loadExifSummary(uri: Uri): ExifSummary? {
        return mediaStorePhotoRepository.readExifSummary(uri)
    }

    fun deletePhoto(uri: Uri) {
        viewModelScope.launch {
            mediaStorePhotoRepository.deletePhoto(uri)
            if (cameraControlFlow.value.lastCapturedUri == uri) {
                cameraControlFlow.value = cameraControlFlow.value.copy(lastCapturedUri = null)
            }
            refreshGallery()
        }
    }

    private fun startLocationUpdates(fineGranted: Boolean) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationRepository.observeLocationUpdates(fineGranted).collect { fix ->
                locationFixFlow.value = fix
                if (fix != null) {
                    resolveAddressForFix(fix)
                }
            }
        }
    }

    private fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
    }

    private fun resolveAddressForFix(fix: LocationFix) {
        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch {
            if (addressFlow.value is AddressState.Idle) {
                addressFlow.value = AddressState.Resolving
            }
            addressFlow.value = offlineGeocoder.resolveAddress(fix.latitude, fix.longitude)
        }
    }

    fun setCoordinateFormat(format: CoordinateFormat) {
        viewModelScope.launch { preferencesRepository.setCoordinateFormat(format) }
    }

    fun setUnitSystem(unitSystem: UnitSystem) {
        viewModelScope.launch { preferencesRepository.setUnitSystem(unitSystem) }
    }

    fun setWatermarkStyle(style: WatermarkStyle) {
        viewModelScope.launch { preferencesRepository.setWatermarkStyle(style) }
    }

    fun setMiniMapSource(source: MiniMapSource) {
        viewModelScope.launch { preferencesRepository.setMiniMapSource(source) }
    }

    fun toggleMiniMapSource() {
        val next = if (uiState.value.overlayConfig.miniMapSource == MiniMapSource.GOOGLE_LIVE_SATELLITE) {
            MiniMapSource.OFFLINE_SATELLITE
        } else {
            MiniMapSource.GOOGLE_LIVE_SATELLITE
        }
        setMiniMapSource(next)
    }

    fun updateOverlayToggle(
        showAddress: Boolean? = null,
        showCoordinates: Boolean? = null,
        showElevation: Boolean? = null,
        showCompass: Boolean? = null,
        showTimestamp: Boolean? = null,
        showAccuracy: Boolean? = null,
        showMiniMap: Boolean? = null,
        showGridLines: Boolean? = null,
        saveOriginalCopy: Boolean? = null,
    ) {
        viewModelScope.launch {
            preferencesRepository.updateToggle(
                showAddress = showAddress,
                showCoordinates = showCoordinates,
                showElevation = showElevation,
                showCompass = showCompass,
                showTimestamp = showTimestamp,
                showAccuracy = showAccuracy,
                showMiniMap = showMiniMap,
                showGridLines = showGridLines,
                saveOriginalCopy = saveOriginalCopy,
            )
        }
    }

    fun setCustomNote(note: String) {
        viewModelScope.launch { preferencesRepository.setCustomNote(note) }
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val context = appContext.applicationContext
            val mediaRepo = MediaStorePhotoRepository(context)
            return MainViewModel(
                locationRepository = LocationRepository(context),
                compassSensorManager = CompassSensorManager(context),
                offlineGeocoder = OfflineGeocoder(context),
                preferencesRepository = OverlayPreferencesRepository(context),
                mediaStorePhotoRepository = mediaRepo,
                photoOverlayProcessor = PhotoOverlayProcessor(context, mediaRepo),
            ) as T
        }
    }
}
