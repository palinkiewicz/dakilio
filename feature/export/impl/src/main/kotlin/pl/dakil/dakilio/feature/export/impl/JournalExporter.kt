package pl.dakil.dakilio.feature.export.impl

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.model.Post

/** How a finished export went. */
data class ExportResult(val entries: Int, val photos: Int)

/** Writes the journal to a file the user picked: Markdown (a ZIP with photos) or a PDF. */
class JournalExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdf: PdfJournalWriter,
    @Dispatcher(DakilioDispatchers.IO) private val io: CoroutineDispatcher,
) {
    /** [onProgress] gets the photos written so far and their total. */
    suspend fun export(
        uri: Uri,
        entries: List<Post>,
        options: ExportOptions,
        labels: ExportLabels,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ExportResult = withContext(io) {
        val job = currentCoroutineContext().job
        val photos = if (options.photos) entries.flatMap { it.photos }.filter { File(it.path).isFile } else emptyList()
        onProgress(0, photos.size)
        try {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't write $uri")
            when (options.format) {
                ExportFormat.Markdown -> writeMarkdown(out, entries, labels, photos.map { it.fileName to File(it.path) }) { done ->
                    job.ensureActive()
                    onProgress(done, photos.size)
                }
                ExportFormat.Pdf -> pdf.write(out, entries, labels, options.photos) { done, total ->
                    job.ensureActive()
                    onProgress(done, total)
                }
            }
        } catch (e: Throwable) {
            // A half-written export would look finished.
            withContext(NonCancellable) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }
            throw e
        }
        ExportResult(entries.size, photos.size)
    }

    /** Plain Markdown, or with [photos] a ZIP holding `journal.md` and `photos/`. */
    private fun writeMarkdown(out: OutputStream, entries: List<Post>, labels: ExportLabels, photos: List<Pair<String, File>>, onPhoto: (Int) -> Unit) {
        val text = renderMarkdown(entries, labels, withPhotos = photos.isNotEmpty()).toByteArray(Charsets.UTF_8)
        if (photos.isEmpty()) {
            out.buffered().use { it.write(text) }
            return
        }
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MARKDOWN_MEMBER))
            zip.write(text)
            zip.closeEntry()
            photos.distinctBy { it.first }.forEachIndexed { index, (name, file) ->
                val bytes = file.readBytes()
                // JPEGs don't compress; storing them is faster and just as small.
                zip.putNextEntry(
                    ZipEntry(markdownPhotoPath(name)).apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = bytes.size.toLong()
                        crc = CRC32().apply { update(bytes) }.value
                    },
                )
                zip.write(bytes)
                zip.closeEntry()
                onPhoto(index + 1)
            }
        }
    }

    private companion object {
        const val MARKDOWN_MEMBER = "journal.md"
    }
}
