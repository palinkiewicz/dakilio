package pl.dakil.dakilio.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
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
import pl.dakil.dakilio.core.database.dao.PostDao

internal class LocalPhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directory: PhotoDirectory,
    private val postDao: PostDao,
    @Dispatcher(DakilioDispatchers.IO) private val io: CoroutineDispatcher,
) : PhotoRepository {

    override suspend fun import(uri: Uri): String? = withContext(io) {
        runCatching {
            // ImageDecoder applies EXIF orientation; re-encoding drops every other EXIF field,
            // GPS location included, so nothing private leaves with a future export.
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longEdge = max(info.size.width, info.size.height)
                if (longEdge > MAX_EDGE_PX) {
                    val scale = MAX_EDGE_PX.toFloat() / longEdge
                    decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val fileName = "${UUID.randomUUID()}.jpg"
            directory.ensureExists()
            directory.file(fileName).outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bitmap.recycle()
            fileName
        }.getOrNull()
    }

    override fun pathOf(fileName: String): String = directory.file(fileName).absolutePath

    override suspend fun delete(fileNames: Collection<String>) {
        if (fileNames.isEmpty()) return
        withContext(io) { fileNames.forEach { directory.file(it).delete() } }
    }

    override suspend fun sweepOrphans() = withContext(io) {
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
