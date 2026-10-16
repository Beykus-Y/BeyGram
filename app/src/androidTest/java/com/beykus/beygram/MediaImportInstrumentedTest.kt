package com.beykus.beygram

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MediaImportInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val importer = OutgoingMediaImporter(context)
    private val prepared = mutableListOf<OutgoingMedia>()
    private lateinit var source: File

    @Before fun setup() { source = context.filesDir.resolve("uploads/test-source-${UUID.randomUUID()}").apply { mkdirs() } }
    @After fun cleanup() = runBlocking { prepared.forEach { importer.discard(it) }; source.deleteRecursively(); Unit }
    private fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    private fun photo(): File {
        val bitmap = Bitmap.createBitmap(3000, 1500, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.BLUE)
            source.resolve("original.png").also { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        } finally { bitmap.recycle() }
    }

    @Test fun photoIsResizedAndThumbnailCanBeSharedWithFileProvider() = runBlocking {
        val original = photo()
        val result = importer.prepare(uri(original), false).also(prepared::add)
        assertEquals(MediaKind.Photo, result.kind)
        assertEquals("image/jpeg", result.mimeType)
        assertEquals(2560, result.width)
        assertEquals(1280, result.height)
        assertEquals(320, result.thumbnailWidth)
        assertEquals(160, result.thumbnailHeight)
        assertTrue(original.exists())
        context.contentResolver.openInputStream(uri(File(result.path)))!!.use { assertEquals(0xFF, it.read()); assertEquals(0xD8, it.read()) }
        assertTrue(File(result.thumbnailPath!!).length() > 0)
    }

    @Test fun documentModePreservesOriginalBytesAndName() = runBlocking {
        val original = photo()
        val result = importer.prepare(uri(original), true).also(prepared::add)
        assertEquals(MediaKind.Document, result.kind)
        assertEquals("original.png", result.fileName)
        assertArrayEquals(original.readBytes(), File(result.path).readBytes())
    }

    @Test fun videoMetadataAndThumbnailArePrepared() = runBlocking {
        val original = source.resolve("sample.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("media-test.mp4").use { from -> original.outputStream().use { from.copyTo(it) } }
        val result = importer.prepare(uri(original), false).also(prepared::add)
        assertEquals(MediaKind.Video, result.kind)
        assertEquals(160, result.width)
        assertEquals(120, result.height)
        assertTrue(result.durationSeconds >= 1)
        assertTrue(File(result.thumbnailPath!!).exists())
        assertArrayEquals(original.readBytes(), File(result.path).readBytes())
    }
}
