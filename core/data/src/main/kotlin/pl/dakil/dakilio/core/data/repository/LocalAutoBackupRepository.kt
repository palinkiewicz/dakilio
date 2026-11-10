package pl.dakil.dakilio.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.dakil.dakilio.core.backup.AutoBackup
import pl.dakil.dakilio.core.common.ApplicationScope
import pl.dakil.dakilio.core.common.DakilioDispatchers
import pl.dakil.dakilio.core.common.Dispatcher
import pl.dakil.dakilio.core.datastore.AutoBackupDataSource
import pl.dakil.dakilio.core.model.AutoBackupConfig
import pl.dakil.dakilio.core.model.AutoBackupFailure
import pl.dakil.dakilio.core.model.AutoBackupFrequency
import pl.dakil.dakilio.core.model.AutoBackupProgress
import pl.dakil.dakilio.core.model.AutoBackupResult
import pl.dakil.dakilio.core.model.AutoBackupStatus

/**
 * Automatic backups through the Storage Access Framework: the folder is a document tree with a
 * persisted grant, so any provider works (local storage, an SD card, a folder another app syncs).
 *
 * A run reads the content and fingerprints it first; when nothing changed since the last backup it
 * stops there, so most runs cost a database read and nothing else. Old backups are deleted only after
 * a new one is complete, so the folder always holds a good one.
 */
internal class LocalAutoBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataSource: AutoBackupDataSource,
    private val writer: BackupWriter,
    private val vault: BackupPasswordVault,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(DakilioDispatchers.IO) private val io: CoroutineDispatcher,
) : AutoBackupRepository {
    private val resolver: ContentResolver get() = context.contentResolver
    private val mutex = Mutex()
    private val _running = MutableStateFlow<AutoBackupProgress?>(null)

    override val config: Flow<AutoBackupConfig> = dataSource.config
    override val status: Flow<AutoBackupStatus> = dataSource.status
    override val running: StateFlow<AutoBackupProgress?> = _running.asStateFlow()

    override suspend fun setFolder(treeUri: Uri) {
        withContext(io) {
            resolver.takePersistableUriPermission(treeUri, GRANT)
            val old = dataSource.config.first().folderUri?.let(Uri::parse)
            if (old != null && old != treeUri) release(old)
            dataSource.setFolder(treeUri.toString(), displayName(treeUri), clock.millis())
        }
        runNow()
    }

    override suspend fun setFrequency(frequency: AutoBackupFrequency) = dataSource.setFrequency(frequency)

    override suspend fun setOnlyWhileCharging(only: Boolean) = dataSource.setOnlyWhileCharging(only)

    override suspend fun setKeep(keep: Int) {
        dataSource.setKeep(keep)
        scope.launch(io) {
            mutex.withLock {
                val tree = dataSource.config.first().folderUri?.let(Uri::parse) ?: return@withLock
                runCatching { prune(tree, list(tree), keep) }.onFailure { Log.w(TAG, "Pruning failed", it) }
            }
        }
    }

    override suspend fun setPassword(password: CharArray?) {
        try {
            if (password == null) {
                dataSource.setPassword(null)
                vault.forget()
            } else {
                dataSource.setPassword(withContext(io) { vault.seal(password) })
            }
        } finally {
            password?.fill('\u0000')
        }
    }

    override suspend fun turnOff() {
        withContext(io) {
            dataSource.config.first().folderUri?.let { release(Uri.parse(it)) }
            dataSource.turnOff()
            vault.forget()
        }
    }

    override fun runNow() {
        scope.launch { run(force = true) }
    }

    override suspend fun runScheduled(): AutoBackupResult? = run(force = false)

    override suspend fun folderBackups(): List<FolderBackup> = withContext(io) {
        val tree = dataSource.config.first().folderUri?.let(Uri::parse) ?: return@withContext emptyList()
        runCatching { list(tree) }.getOrDefault(emptyList())
            .mapNotNull { doc -> AutoBackup.parseFileName(doc.name)?.let { FolderBackup(doc.uri, doc.name, it, doc.bytes) } }
            .sortedByDescending { it.time }
    }

    // A run.

    private suspend fun run(force: Boolean): AutoBackupResult? = withContext(io) {
        mutex.withLock {
            val config = dataSource.config.first()
            val tree = config.folderUri?.let(Uri::parse) ?: return@withLock null
            val outcome = try {
                attempt(config, tree, force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Automatic backup failed", e)
                Outcome(AutoBackupResult.Failed(e.toFailure()))
            } finally {
                _running.value = null
            }
            withContext(NonCancellable) { dataSource.recordResult(outcome.result, clock.millis(), outcome.fingerprint) }
            outcome.result
        }
    }

    private class Outcome(val result: AutoBackupResult, val fingerprint: String? = null)

    private suspend fun attempt(config: AutoBackupConfig, tree: Uri, force: Boolean): Outcome {
        if (resolver.persistedUriPermissions.none { it.uri == tree && it.isWritePermission }) {
            return Outcome(AutoBackupResult.Failed(AutoBackupFailure.FolderGone))
        }
        val existing = list(tree)
        val prepared = writer.prepare()
        val fingerprint = AutoBackup.fingerprint(prepared.backup, prepared.photoSizes())
        // Unchanged, and the last backup is still there: nothing to do. This is how most runs end.
        if (!force && fingerprint == dataSource.fingerprint() && existing.any { AutoBackup.parseFileName(it.name) != null }) {
            return Outcome(AutoBackupResult.Unchanged)
        }

        val password = if (config.hasPassword) {
            val sealed = dataSource.password() ?: return Outcome(AutoBackupResult.Failed(AutoBackupFailure.PasswordUnavailable))
            vault.open(sealed) ?: return Outcome(AutoBackupResult.Failed(AutoBackupFailure.PasswordUnavailable))
        } else {
            null
        }

        val name = AutoBackup.fileName(LocalDateTime.now(clock.withZone(ZoneId.systemDefault())))
        // Two runs within a minute share a name; the newer one replaces the older.
        val target = existing.firstOrNull { it.name == name }?.uri
            ?: DocumentsContract.createDocument(resolver, treeDocument(tree), MIME, name)
            ?: throw IOException("Can't create $name")
        val job = currentCoroutineContext().job
        var done = 0
        _running.value = AutoBackupProgress(0, prepared.photoCount)
        try {
            val out = resolver.openOutputStream(target, "wt") ?: throw IOException("Can't write $name")
            writer.write(out, prepared, password) {
                job.ensureActive()
                _running.value = AutoBackupProgress(++done, prepared.photoCount)
            }
        } catch (e: Throwable) {
            // A half-written file would look like a backup.
            withContext(NonCancellable) { runCatching { DocumentsContract.deleteDocument(resolver, target) } }
            throw e
        } finally {
            password?.fill('\u0000')
        }

        val after = runCatching { list(tree) }.getOrDefault(existing)
        runCatching { prune(tree, after, config.keep) }.onFailure { Log.w(TAG, "Pruning failed", it) }
        val bytes = after.firstOrNull { it.name == name }?.bytes ?: 0L
        val result = AutoBackupResult.Written(prepared.backup.entries.size, prepared.photoCount, bytes)
        return Outcome(result, fingerprint)
    }

    /** Deletes the automatic backups past the newest [keep]. Other files are never touched. */
    private fun prune(tree: Uri, docs: List<Doc>, keep: Int) {
        val doomed = AutoBackup.filesToDelete(docs.map { it.name }, keep).toSet()
        docs.filter { it.name in doomed }.forEach { doc ->
            runCatching { DocumentsContract.deleteDocument(resolver, doc.uri) }.onFailure { Log.w(TAG, "Can't delete ${doc.name}", it) }
        }
    }

    // Documents.

    private class Doc(val uri: Uri, val name: String, val bytes: Long)

    private fun treeDocument(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** The folder's files (not recursive). Throws when the folder can't be read. */
    private fun list(tree: Uri): List<Doc> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE, Document.COLUMN_MIME_TYPE)
        val cursor = resolver.query(children, columns, null, null, null) ?: throw FileNotFoundException("Can't list $tree")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    if (it.getString(3) == Document.MIME_TYPE_DIR) continue
                    val name = it.getString(1) ?: continue
                    val size = if (it.isNull(2)) 0L else it.getLong(2)
                    add(Doc(DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0)), name, size))
                }
            }
        }
    }

    private fun displayName(tree: Uri): String? = runCatching {
        resolver.query(treeDocument(tree), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun release(tree: Uri) {
        runCatching { resolver.releasePersistableUriPermission(tree, GRANT) }
    }

    private fun Exception.toFailure(): AutoBackupFailure {
        val messages = generateSequence<Throwable>(this) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        return when {
            "ENOSPC" in messages || "No space" in messages -> AutoBackupFailure.NoSpace
            this is SecurityException || this is FileNotFoundException || this is IllegalArgumentException -> AutoBackupFailure.FolderGone
            else -> AutoBackupFailure.Other
        }
    }

    private companion object {
        const val TAG = "DakilioAutoBackup"
        const val MIME = "application/octet-stream"
        const val GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
