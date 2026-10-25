package com.beykus.beygram.ui.screens.chat

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.beykus.beygram.domain.model.MessageMedia
import java.io.File

/**
 * Copies one downloaded attachment into the public gallery (photos/videos) or Downloads. Unlike the media viewer this
 * is used for a batch, so every file is handled independently and an unsupported type still has a safe fallback.
 */
internal fun saveSelectedMediaToDevice(context: Context, path: String, media: MessageMedia): Result<Unit> = runCatching {
    val resolver = context.contentResolver
    val mimeMap = MimeTypeMap.getSingleton()
    val export = resolveExportFileMetadata(
        media.fileName,
        media.mimeType,
        media.kind.label(context),
        mimeMap::getExtensionFromMimeType,
        mimeMap::getMimeTypeFromExtension,
        readExportSignature(File(path))
    )
    val image = export.mimeType.startsWith("image/")
    val video = export.mimeType.startsWith("video/")
    val (collection, folder) = when {
        image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
        video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
        else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
    }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, export.displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, export.mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/BeyGram")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(collection, values) ?: error("MediaStore did not create a record")
    try {
        val output = resolver.openOutputStream(uri) ?: error("MediaStore did not open an output stream")
        output.use { stream -> File(path).inputStream().use { input -> input.copyTo(stream) } }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (failure: Exception) {
        resolver.delete(uri, null, null)
        throw failure
    }
    Unit
}
