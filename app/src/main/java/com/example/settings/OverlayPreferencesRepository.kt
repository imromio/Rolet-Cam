package com.example.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CameraAspectRatio
import com.example.model.CoordinateFormat
import com.example.model.MiniMapSource
import com.example.model.OverlayConfig
import com.example.model.UnitSystem
import com.example.model.WatermarkStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.geoLensDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "geolens_preferences",
)

class OverlayPreferencesRepository(private val context: Context) {

    private object Keys {
        val COORDINATE_FORMAT = stringPreferencesKey("coordinate_format")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val WATERMARK_STYLE = stringPreferencesKey("watermark_style")
        val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
        val MINI_MAP_SOURCE = stringPreferencesKey("mini_map_source")
        val SHOW_ADDRESS = booleanPreferencesKey("show_address")
        val SHOW_COORDINATES = booleanPreferencesKey("show_coordinates")
        val SHOW_ELEVATION = booleanPreferencesKey("show_elevation")
        val SHOW_COMPASS = booleanPreferencesKey("show_compass")
        val SHOW_TIMESTAMP = booleanPreferencesKey("show_timestamp")
        val SHOW_ACCURACY = booleanPreferencesKey("show_accuracy")
        val SHOW_MINIMAP = booleanPreferencesKey("show_minimap")
        val SHOW_GRID_LINES = booleanPreferencesKey("show_grid_lines")
        val SAVE_ORIGINAL_COPY = booleanPreferencesKey("save_original_copy")
        val CUSTOM_NOTE = stringPreferencesKey("custom_note")
        val HAS_REQUESTED_PERMISSIONS = booleanPreferencesKey("has_requested_permissions")
    }

    val overlayConfigFlow: Flow<OverlayConfig> = context.geoLensDataStore.data.map { prefs ->
        OverlayConfig(
            coordinateFormat = prefs[Keys.COORDINATE_FORMAT]?.let {
                runCatching { CoordinateFormat.valueOf(it) }.getOrNull()
            } ?: CoordinateFormat.DECIMAL_DEGREES,
            unitSystem = prefs[Keys.UNIT_SYSTEM]?.let {
                runCatching { UnitSystem.valueOf(it) }.getOrNull()
            } ?: UnitSystem.METRIC,
            watermarkStyle = prefs[Keys.WATERMARK_STYLE]?.let {
                runCatching { WatermarkStyle.valueOf(it) }.getOrNull()
            } ?: WatermarkStyle.TACTICAL_CARD,
            aspectRatio = prefs[Keys.ASPECT_RATIO]?.let {
                runCatching { CameraAspectRatio.valueOf(it) }.getOrNull()
            } ?: CameraAspectRatio.FOUR_THREE,
            miniMapSource = prefs[Keys.MINI_MAP_SOURCE]?.let {
                runCatching { MiniMapSource.valueOf(it) }.getOrNull()
            } ?: MiniMapSource.OFFLINE_SATELLITE,
            showAddress = prefs[Keys.SHOW_ADDRESS] ?: true,
            showCoordinates = prefs[Keys.SHOW_COORDINATES] ?: true,
            showElevation = prefs[Keys.SHOW_ELEVATION] ?: false,
            showCompass = prefs[Keys.SHOW_COMPASS] ?: false,
            showTimestamp = prefs[Keys.SHOW_TIMESTAMP] ?: true,
            showAccuracy = prefs[Keys.SHOW_ACCURACY] ?: true,
            showMiniMap = prefs[Keys.SHOW_MINIMAP] ?: true,
            showGridLines = prefs[Keys.SHOW_GRID_LINES] ?: true,
            saveOriginalCopy = prefs[Keys.SAVE_ORIGINAL_COPY] ?: false,
            customNote = prefs[Keys.CUSTOM_NOTE] ?: "",
        )
    }

    val hasRequestedPermissionsFlow: Flow<Boolean> = context.geoLensDataStore.data.map { prefs ->
        prefs[Keys.HAS_REQUESTED_PERMISSIONS] ?: false
    }

    suspend fun markPermissionsRequested() {
        context.geoLensDataStore.edit { prefs ->
            prefs[Keys.HAS_REQUESTED_PERMISSIONS] = true
        }
    }

    suspend fun setCoordinateFormat(format: CoordinateFormat) {
        context.geoLensDataStore.edit { it[Keys.COORDINATE_FORMAT] = format.name }
    }

    suspend fun setUnitSystem(unitSystem: UnitSystem) {
        context.geoLensDataStore.edit { it[Keys.UNIT_SYSTEM] = unitSystem.name }
    }

    suspend fun setWatermarkStyle(style: WatermarkStyle) {
        context.geoLensDataStore.edit { it[Keys.WATERMARK_STYLE] = style.name }
    }

    suspend fun setAspectRatio(aspectRatio: CameraAspectRatio) {
        context.geoLensDataStore.edit { it[Keys.ASPECT_RATIO] = aspectRatio.name }
    }

    suspend fun setMiniMapSource(source: MiniMapSource) {
        context.geoLensDataStore.edit { it[Keys.MINI_MAP_SOURCE] = source.name }
    }

    suspend fun updateToggle(
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
        context.geoLensDataStore.edit { prefs ->
            showAddress?.let { prefs[Keys.SHOW_ADDRESS] = it }
            showCoordinates?.let { prefs[Keys.SHOW_COORDINATES] = it }
            showElevation?.let { prefs[Keys.SHOW_ELEVATION] = it }
            showCompass?.let { prefs[Keys.SHOW_COMPASS] = it }
            showTimestamp?.let { prefs[Keys.SHOW_TIMESTAMP] = it }
            showAccuracy?.let { prefs[Keys.SHOW_ACCURACY] = it }
            showMiniMap?.let { prefs[Keys.SHOW_MINIMAP] = it }
            showGridLines?.let { prefs[Keys.SHOW_GRID_LINES] = it }
            saveOriginalCopy?.let { prefs[Keys.SAVE_ORIGINAL_COPY] = it }
        }
    }

    suspend fun setCustomNote(note: String) {
        context.geoLensDataStore.edit { it[Keys.CUSTOM_NOTE] = note.take(64) }
    }
}
