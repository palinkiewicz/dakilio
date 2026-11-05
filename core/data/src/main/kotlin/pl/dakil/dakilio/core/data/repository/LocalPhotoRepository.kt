package pl.dakil.dakilio.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.dakil.dakilio.core.common.ApplicationScope
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.common.PhotoDirectory
import pl.dakil.dakilio.core.database.DakilioDatabase
import pl.dakil.dakilio.core.database.dao.PostDao
import pl.dakil.dakilio.core.datastore.SettingsDataSource
import pl.dakil.dakilio.core.model.PhotoSize
import pl.dakil.dakilio.core.model.PhotoSizing
import pl.dakil.dakilio.core.model.PixelSize

internal class LocalPhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directory: PhotoDirectory,
    private val postDao: PostDao,
    private val settings: SettingsDataSource,
    @Dispatcher(DakilioDispatchers.IO) private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) : PhotoRepository {

    private val _shrinking = MutableStateFlow(false)
    override val shrinking: StateFlow<Boolean> = _shrinking.asStateFlow()

    /**
     * Pixel sizes of stored photos, read from their headers once per app run and kept current by
     * every write here, so only the first check after a launch touches the files.
     */
    private val sizes = ConcurrentHashMap<String, PixelSize>()

    /** Bumped when a stored photo's size changes in place (shrinking), which entries don't notice. */
    private val sizesChanged = MutableStateFlow(0)

    override suspend fun import(uri: Uri): String? = withContext(io) {
        val size = currentSize()
        runCatching { decodeAndStore(ImageDecoder.createSource(context.contentResolver, uri), rotationDegrees = 0, size) }.getOrNull()
    }

    override suspend fun importBytes(bytes: ByteArray, rotationDegrees: Int, size: PhotoSize?): String? = withContext(io) {
        val photoSize = size ?: currentSize()
        runCatching {
            // Only apply the extra rotation when the image doesn't carry its own EXIF orientation,
            // which ImageDecoder already honours.
            val hasExifOrientation = ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED) != ExifInterface.ORIENTATION_UNDEFINED
            decodeAndStore(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), if (hasExifOrientation) 0 else rotationDegrees, photoSize)
        }.getOrNull()
    }

    private suspend fun currentSize(): PhotoSize = settings.settings.first().photoSize

    private fun decodeAndStore(source: ImageDecoder.Source, rotationDegrees: Int, size: PhotoSize): String {
        // ImageDecoder applies EXIF orientation; re-encoding drops every other EXIF field,
        // GPS location included, so nothing private leaves with a future export.
        val decoded = decode(source, size)
        val bitmap = if (rotationDegrees % 360 != 0) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        val fileName = "${UUID.randomUUID()}.jpg"
        directory.ensureExists()
        val file = directory.file(fileName)
        val stored = PixelSize(bitmap.width, bitmap.height)
        try {
            writeJpeg(bitmap, file)
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
        sizes[fileName] = stored
        return fileName
    }

    /** Decodes [source] into memory, scaled down to fit [size]. */
    private fun decode(source: ImageDecoder.Source, size: PhotoSize): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            PhotoSizing.scaledSize(info.size.width, info.size.height, size.maxEdge)
                ?.let { decoder.setTargetSize(it.width, it.height) }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun writeJpeg(bitmap: Bitmap, file: File) {
        file.outputStream().buffered().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) }
    }

    override fun pathOf(fileName: String): String = directory.file(fileName).absolutePath

    override suspend fun delete(fileNames: Collection<String>) {
        if (fileNames.isEmpty()) return
        withContext(io) {
            fileNames.forEach {
                directory.file(it).delete()
                sizes.remove(it)
            }
        }
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
            ?.forEach {
                it.delete()
                sizes.remove(it.name)
            }
        Unit
    }

    /**
     * The size of a stored photo: from the cache, else from the file's header (a few hundred bytes;
     * the pixels aren't decoded). Null when the file is missing or unreadable.
     */
    private fun sizeOf(fileName: String): PixelSize? = sizes[fileName] ?: run {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(directory.file(fileName).path, options)
        if (options.outWidth > 0 && options.outHeight > 0) PixelSize(options.outWidth, options.outHeight).also { sizes[fileName] = it } else null
    }

    private fun isLargerThan(fileName: String, size: PhotoSize): Boolean = sizeOf(fileName)?.let { PhotoSizing.exceeds(it, size) } == true

    override fun countLargerThan(size: PhotoSize): Flow<Int> {
        if (size.maxEdge == null) return flowOf(0)
        return combine(postDao.observePhotoFileNames(), sizesChanged) { names, _ -> names }
            .mapLatest { names -> withContext(io) { names.count { isLargerThan(it, size) } } }
            .distinctUntilChanged()
    }

    override fun shrinkAll(size: PhotoSize) {
        if (size.maxEdge == null) return
        if (!_shrinking.compareAndSet(expect = false, update = true)) return
        // The app's scope, so leaving the screen doesn't stop it halfway.
        scope.launch(io) {
            try {
                postDao.allPhotoFileNames().distinct().filter { isLargerThan(it, size) }.forEach { fileName ->
                    ensureActive()
                    runCatching { shrink(fileName, size) }
                    sizesChanged.value++
                }
            } finally {
                _shrinking.value = false
            }
        }
    }

    /**
     * Rewrites one photo scaled down to [size], under the same name, so no entry changes. The new
     * file is written beside it and moved over it in one step: the photo is always either the old
     * one or the new one. A leftover temporary file is unreferenced, so [sweepOrphans] removes it.
     */
    private fun shrink(fileName: String, size: PhotoSize) {
        val file = directory.file(fileName)
        if (!file.isFile) return
        val bitmap = decode(ImageDecoder.createSource(file), size)
        val temp = directory.file("$fileName.tmp")
        val stored = PixelSize(bitmap.width, bitmap.height)
        try {
            writeJpeg(bitmap, temp)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            temp.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
        sizes[fileName] = stored
    }

    private companion object {
        /** JPEG quality of stored photos: the usual web standard, far smaller than 90+ and hard to tell apart. */
        const val JPEG_QUALITY = 80
    }
}
