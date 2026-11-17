package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.location.CoordinateFormatter
import com.example.model.CapturedPhoto
import com.example.model.CoordinateFormat
import com.example.model.ExifSummary
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PrecisionAmber
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TelemetryTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    photos: List<CapturedPhoto>,
    onBack: () -> Unit,
    onOpenInDefaultGallery: (Uri?) -> Unit,
    onLoadExif: suspend (Uri) -> ExifSummary?,
    onDeletePhoto: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedPhoto by remember { mutableStateOf<CapturedPhoto?>(null) }
    var exifSummary by remember { mutableStateOf<ExifSummary?>(null) }
    var showExifSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedPhoto != null) {
        selectedPhoto = null
        showExifSheet = false
    }

    LaunchedEffect(selectedPhoto) {
        exifSummary = selectedPhoto?.let { onLoadExif(it.uri) }
    }

    if (selectedPhoto != null) {
        val current = selectedPhoto!!
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = current.uri,
                contentDescription = current.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = Color(0xA6141619), shape = RoundedCornerShape(999.dp)) {
                    IconButton(
                        onClick = { selectedPhoto = null },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button),
                            tint = Color.White,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = Color(0xA6141619), shape = RoundedCornerShape(999.dp)) {
                        IconButton(
                            onClick = { onOpenInDefaultGallery(current.uri) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("open_default_gallery_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = stringResource(R.string.open_in_gallery),
                                tint = Color.White,
                            )
                        }
                    }

                    Surface(color = Color(0xA6141619), shape = RoundedCornerShape(999.dp)) {
                        IconButton(
                            onClick = { showExifSheet = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("exif_info_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = stringResource(R.string.cd_photo_info),
                                tint = PrecisionAmber,
                            )
                        }
                    }

                    Surface(color = Color(0xA6141619), shape = RoundedCornerShape(999.dp)) {
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, current.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Rolet Cam Photo"))
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.cd_share_photo),
                                tint = Color.White,
                            )
                        }
                    }

                    Surface(color = Color(0xA6141619), shape = RoundedCornerShape(999.dp)) {
                        IconButton(
                            onClick = {
                                onDeletePhoto(current.uri)
                                selectedPhoto = null
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.cd_delete_photo),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }

        if (showExifSheet) {
            ModalBottomSheet(
                onDismissRequest = { showExifSheet = false },
                containerColor = SlateSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Embedded EXIF Metadata",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        text = current.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val summary = exifSummary
                    if (summary != null) {
                        val coords = if (summary.latitude != null && summary.longitude != null) {
                            CoordinateFormatter.formatCoordinates(
                                summary.latitude,
                                summary.longitude,
                                CoordinateFormat.DECIMAL_DEGREES,
                            )
                        } else {
                            "No GPS EXIF coordinates"
                        }
                        ExifRow("GPS Coordinates", coords)
                        ExifRow("Date / Time", summary.dateTimeOriginal ?: "--")
                        ExifRow(
                            "Altitude",
                            summary.altitudeMeters?.let { String.format(java.util.Locale.US, "%.1f m", it) } ?: "--",
                        )
                        ExifRow("Dimensions", "${summary.width} × ${summary.height} px")
                        summary.description?.let { ExifRow("Location Tag", it) }
                    } else {
                        Text("Reading EXIF headers…", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianVoid,
        topBar = {
            TopAppBar(
                title = { Text("Camera Photos (${photos.size})") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onOpenInDefaultGallery(photos.firstOrNull()?.uri) },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = PrecisionAmber,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(R.string.open_in_gallery), color = PrecisionAmber)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlateSurface,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = null,
                        tint = PrecisionAmber,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No geotagged photos yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Captured photos save directly to DCIM/Camera.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                contentPadding = PaddingValues(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(photos, key = { it.id }) { photo ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SlateOutline.copy(alpha = 0.5f)),
                        color = SlateContainer,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedPhoto = photo },
                    ) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = photo.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExifRow(label: String, value: String) {
    Surface(
        color = SlateContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = TelemetryTextStyle.copy(color = Color.White))
        }
    }
}
