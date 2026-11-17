package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CameraAspectRatio
import com.example.model.CoordinateFormat
import com.example.model.MiniMapSource
import com.example.model.UnitSystem
import com.example.model.WatermarkStyle
import com.example.ui.GeoLensUiState
import com.example.ui.components.WatermarkPreviewCard
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PrecisionAmber
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurface

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OverlaySettingsScreen(
    uiState: GeoLensUiState,
    onBack: () -> Unit,
    onCoordinateFormatChange: (CoordinateFormat) -> Unit,
    onUnitSystemChange: (UnitSystem) -> Unit,
    onWatermarkStyleChange: (WatermarkStyle) -> Unit,
    onAspectRatioChange: (CameraAspectRatio) -> Unit,
    onMiniMapSourceChange: (MiniMapSource) -> Unit,
    onToggleChange: (
        showAddress: Boolean?,
        showCoordinates: Boolean?,
        showElevation: Boolean?,
        showCompass: Boolean?,
        showTimestamp: Boolean?,
        showAccuracy: Boolean?,
        showMiniMap: Boolean?,
        showGridLines: Boolean?,
        saveOriginalCopy: Boolean?,
    ) -> Unit,
    onCustomNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = uiState.overlayConfig

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianVoid,
        topBar = {
            TopAppBar(
                title = { Text("Overlay & Camera Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlateSurface,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    WatermarkPreviewCard(
                        locationFix = uiState.locationFix,
                        compassState = uiState.compassState,
                        addressState = uiState.addressState,
                        overlayConfig = config,
                        currentTimeMillis = uiState.currentTimeMillis,
                    )
                }

                item {
                    SettingsSectionCard(title = "CAMERA ASPECT RATIO") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CameraAspectRatio.entries.forEach { ratio ->
                                FilterChip(
                                    selected = config.aspectRatio == ratio,
                                    onClick = { onAspectRatioChange(ratio) },
                                    label = { Text(ratio.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrecisionAmber.copy(alpha = 0.2f),
                                        selectedLabelColor = PrecisionAmber,
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("aspect_chip_${ratio.name}"),
                                )
                            }
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "MINI-MAP SATELLITE LAYER") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MiniMapSource.entries.forEach { source ->
                                val selected = config.miniMapSource == source
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selected,
                                            onClick = { onMiniMapSourceChange(source) },
                                            role = Role.RadioButton,
                                        )
                                        .minimumInteractiveComponentSize()
                                        .padding(vertical = 4.dp)
                                        .testTag("minimap_radio_${source.name}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = PrecisionAmber,
                                            unselectedColor = Color(0xFF94A3B8),
                                        ),
                                    )
                                    Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = source.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (selected) PrecisionAmber else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = source.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "COORDINATE FORMAT") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoordinateFormat.entries.forEach { format ->
                                FilterChip(
                                    selected = config.coordinateFormat == format,
                                    onClick = { onCoordinateFormatChange(format) },
                                    label = { Text("${format.shortLabel} • ${format.displayName}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrecisionAmber.copy(alpha = 0.2f),
                                        selectedLabelColor = PrecisionAmber,
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("format_chip_${format.name}"),
                                )
                            }
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "UNITS & STAMP STYLE") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UnitSystem.entries.forEach { unit ->
                                FilterChip(
                                    selected = config.unitSystem == unit,
                                    onClick = { onUnitSystemChange(unit) },
                                    label = { Text(unit.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrecisionAmber.copy(alpha = 0.2f),
                                        selectedLabelColor = PrecisionAmber,
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("unit_chip_${unit.name}"),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WatermarkStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = config.watermarkStyle == style,
                                    onClick = { onWatermarkStyleChange(style) },
                                    label = { Text(style.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrecisionAmber.copy(alpha = 0.2f),
                                        selectedLabelColor = PrecisionAmber,
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("style_chip_${style.name}"),
                                )
                            }
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "PHOTO STAMP FIELDS") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OverlaySwitchRow(
                                label = "Street / Locality Address & Plus Code",
                                checked = config.showAddress,
                                onCheckedChange = { onToggleChange(it, null, null, null, null, null, null, null, null) },
                                tag = "toggle_address",
                            )
                            OverlaySwitchRow(
                                label = "Latitude & Longitude",
                                checked = config.showCoordinates,
                                onCheckedChange = { onToggleChange(null, it, null, null, null, null, null, null, null) },
                                tag = "toggle_coordinates",
                            )
                            OverlaySwitchRow(
                                label = "Date & Time Stamp",
                                checked = config.showTimestamp,
                                onCheckedChange = { onToggleChange(null, null, null, null, it, null, null, null, null) },
                                tag = "toggle_timestamp",
                            )
                            OverlaySwitchRow(
                                label = "GPS Accuracy (±m / ±ft)",
                                checked = config.showAccuracy,
                                onCheckedChange = { onToggleChange(null, null, null, null, null, it, null, null, null) },
                                tag = "toggle_accuracy",
                            )
                            OverlaySwitchRow(
                                label = "Satellite Mini-Map Thumbnail",
                                checked = config.showMiniMap,
                                onCheckedChange = { onToggleChange(null, null, null, null, null, null, it, null, null) },
                                tag = "toggle_minimap",
                            )
                            OverlaySwitchRow(
                                label = "Elevation / Altitude",
                                checked = config.showElevation,
                                onCheckedChange = { onToggleChange(null, null, it, null, null, null, null, null, null) },
                                tag = "toggle_elevation",
                            )
                            OverlaySwitchRow(
                                label = "Compass Bearing",
                                checked = config.showCompass,
                                onCheckedChange = { onToggleChange(null, null, null, it, null, null, null, null, null) },
                                tag = "toggle_compass",
                            )
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "CAMERA & STORAGE") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OverlaySwitchRow(
                                label = "Viewfinder Rule-of-Thirds Grid",
                                checked = config.showGridLines,
                                onCheckedChange = { onToggleChange(null, null, null, null, null, null, null, it, null) },
                                tag = "toggle_grid",
                            )
                            OverlaySwitchRow(
                                label = "Also Save Unstamped Original Photo",
                                checked = config.saveOriginalCopy,
                                onCheckedChange = { onToggleChange(null, null, null, null, null, null, null, null, it) },
                                tag = "toggle_save_original",
                            )
                        }
                    }
                }

                item {
                    SettingsSectionCard(title = "CUSTOM FIELD NOTE") {
                        OutlinedTextField(
                            value = config.customNote,
                            onValueChange = onCustomNoteChange,
                            label = { Text("Project / Site Note (optional)") },
                            placeholder = { Text("e.g., Site Survey #14 - North Ridge") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_note_input"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateContainer),
        border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.6f)),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun OverlaySwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
        )
    }
}
