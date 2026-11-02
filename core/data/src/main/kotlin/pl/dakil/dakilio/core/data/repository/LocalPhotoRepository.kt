package pl.dakil.dakilio.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.common.PhotoDirectory
import pl.dakil.dakilio.core.database.DakilioDatabase
import pl.dakil.dakilio.core.database.dao.PostDao

internal class LocalPhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directory: PhotoDirectory,
    private val postDao: PostDao,
    @Dispatcher(DakilioDispatchers.IO) private val io: CoroutineDispatcher,
) : PhotoRepository {

    override suspend fun import(uri: Uri): String? = withContext(io) {
        runCatching { decodeAndStore(ImageDecoder.createSource(context.contentResolver, uri), rotationDegrees = 0) }.getOrNull()
    }

    override suspend fun importBytes(bytes: ByteArray, rotationDegrees: Int): String? = withContext(io) {
        runCatching {
            // Only apply the extra rotation when the image doesn't carry its own EXIF orientation,
            // which ImageDecoder already honours.
            val hasExifOrientation = ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED) != ExifInterface.ORIENTATION_UNDEFINED
            decodeAndStore(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), if (hasExifOrientation) 0 else rotationDegrees)
        }.getOrNull()
    }

    private fun decodeAndStore(source: ImageDecoder.Source, rotationDegrees: Int): String {
        // ImageDecoder applies EXIF orientation; re-encoding drops every other EXIF field,
        // GPS location included, so nothing private leaves with a future export.
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > MAX_EDGE_PX) {
                val scale = MAX_EDGE_PX.toFloat() / longEdge
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val bitmap = if (rotationDegrees % 360 != 0) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        val fileName = "${UUID.randomUUID()}.jpg"
        directory.ensureExists()
        try {
            directory.file(fileName).outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } catch (e: Exception) {
            directory.file(fileName).delete()
            throw e
        } finally {
            bitmap.recycle()
        }
        return fileName
    }

    override fun pathOf(fileName: String): String = directory.file(fileName).absolutePath

    override suspend fun delete(fileNames: Collection<String>) {
        if (fileNames.isEmpty()) return
        withContext(io) { fileNames.forEach { directory.file(it).delete() } }
    }

    override suspend fun sweepOrphans() = withContext(io) {
        // A fresh install has nothing to sweep, and querying would create (and seed) the database
        // before the first-run intro has let the user pick a language.
        if (!DakilioDatabase.exists(context)) return@withContext
        val referenced = postDao.allPhotoFileNames().toSet()
        // Leave recent files alone: they may belong to an entry that is still being written.
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        directory.root.listFiles()
            ?.filter { it.name !in referenced && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
        Unit
    }

    private companion object {
        const val MAX_EDGE_PX = 2560
        const val JPEG_QUALITY = 88
    }
}
