package com.example.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.model.CapturedPhoto
import com.example.model.ExifSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MediaStorePhotoRepository(private val context: Context) {

    suspend fun saveJpegToMediaStore(
        jpegFile: File,
        displayName: String,
        dateTakenMillis: Long,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_TAKEN, dateTakenMillis)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Camera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = resolver.insert(collection, values)
                    ?: return@withContext Result.failure(IllegalStateException("MediaStore insert returned null"))

                try {
                    resolver.openOutputStream(uri, "w")?.use { out ->
                        jpegFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    } ?: throw IllegalStateException("Could not open MediaStore output stream")

                    val publish = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, publish, null, null)
                    Result.success(uri)
                } catch (t: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    Result.failure(t)
                }
            } else {
                @Suppress("DEPRECATION")
                val dcimDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    "Camera",
                )
                if (!dcimDir.exists()) {
                    dcimDir.mkdirs()
                }
                val destFile = File(dcimDir, displayName)
                jpegFile.inputStream().use { input ->
                    FileOutputStream(destFile).use { out ->
                        input.copyTo(out)
                    }
                }

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_TAKEN, dateTakenMillis)
                    @Suppress("DEPRECATION")
                    put(MediaStore.Images.Media.DATA, destFile.absolutePath)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null,
                )
                if (uri != null) {
                    Result.success(uri)
                } else {
                    Result.success(Uri.fromFile(destFile))
                }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun queryCapturedPhotos(): List<CapturedPhoto> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<CapturedPhoto>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
        )

        val selection =
            "(${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?)"
        val selectionArgs = arrayOf("IMG_GEOLENS_%", "GEOLENS_%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val dateTaken = cursor.getLong(takenCol).takeIf { it > 0L }
                        ?: (cursor.getLong(addedCol) * 1000L)
                    photos.add(
                        CapturedPhoto(
                            id = id,
                            uri = contentUri,
                            displayName = cursor.getString(nameCol) ?: "Rolet Cam Photo",
                            dateTakenMillis = dateTaken,
                            sizeBytes = cursor.getLong(sizeCol),
                        )
                    )
                }
            }
        }
        photos
    }

    suspend fun readExifSummary(uri: Uri): ExifSummary? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = exif.latLong
                val altitude = exif.getAltitude(Double.NaN).takeIf { !it.isNaN() }
                ExifSummary(
                    latitude = latLong?.getOrNull(0),
                    longitude = latLong?.getOrNull(1),
                    altitudeMeters = altitude,
                    dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
                    width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0),
                    height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0),
                    description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION),
                )
            }
        }.getOrNull()
    }

    suspend fun deletePhoto(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }
}
