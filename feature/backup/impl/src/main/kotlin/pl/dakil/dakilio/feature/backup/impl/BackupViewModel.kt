package pl.dakil.dakilio.feature.backup.impl

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.backup.AutoBackup
import pl.dakil.dakilio.core.backup.BackupError
import pl.dakil.dakilio.core.backup.BackupException
import pl.dakil.dakilio.core.data.repository.BackupExportResult
import pl.dakil.dakilio.core.data.repository.BackupOpenResult
import pl.dakil.dakilio.core.data.repository.BackupPreview
import pl.dakil.dakilio.core.data.repository.AutoBackupRepository
import pl.dakil.dakilio.core.data.repository.BackupRepository
import pl.dakil.dakilio.core.data.repository.BackupRestoreResult
import pl.dakil.dakilio.core.data.repository.FolderBackup
import pl.dakil.dakilio.core.data.repository.IncomingBackups
import pl.dakil.dakilio.core.model.AutoBackupConfig
import pl.dakil.dakilio.core.model.AutoBackupFrequency
import pl.dakil.dakilio.core.model.AutoBackupProgress
import pl.dakil.dakilio.core.model.AutoBackupStatus

internal enum class BackupStage { Export, Open, Restore }

internal sealed interface BackupUiState {
    /** The start: create a backup or pick one to restore. */
    data object Home : BackupUiState

    /** [total] photos to write; before the first one the key is being derived (for a password). */
    data class Exporting(val done: Int, val total: Int) : BackupUiState

    data class Exported(val result: BackupExportResult) : BackupUiState

    data object Reading : BackupUiState

    /** An encrypted backup waits for its password; [wrong] after a failed try. */
    data class Password(val wrong: Boolean) : BackupUiState

    data object Unlocking : BackupUiState

    data class Preview(val preview: BackupPreview) : BackupUiState

    /** [total] photos to copy; once [done] reaches it, the database swap is running. */
    data class Restoring(val done: Int, val total: Int) : BackupUiState

    data class Restored(val result: BackupRestoreResult) : BackupUiState

    /** [reason] is null for an unexpected failure (I/O, database…). */
    data class Failed(val reason: BackupError?, val stage: BackupStage) : BackupUiState
}

/** Automatic backups as the screen shows them. */
internal data class AutoBackupUi(
    val config: AutoBackupConfig,
    val status: AutoBackupStatus,
    val running: AutoBackupProgress?,
    /** No backup for longer than the frequency allows (see `AutoBackup.isOverdue`). */
    val overdue: Boolean,
    /** The automatic backups in the folder, newest first. */
    val folderBackups: List<FolderBackup>,
)

/** States the user must not leave: an export or restore in flight, or a key being derived. */
internal val BackupUiState.isBusy: Boolean
    get() = this is BackupUiState.Exporting || this is BackupUiState.Restoring || this is BackupUiState.Unlocking

@HiltViewModel
internal class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
    private val autoBackup: AutoBackupRepository,
    private val clock: Clock,
    incoming: IncomingBackups,
) : ViewModel() {
    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Home)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private var job: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val folderBackups = combine(autoBackup.config.map { it.folderUri }, autoBackup.status) { folder, status -> folder to status.lastRunAt }
        .distinctUntilChanged()
        .mapLatest { autoBackup.folderBackups() }

    /** Null until it's loaded, so the screen doesn't flash "off" for a moment. */
    val auto: StateFlow<AutoBackupUi?> = combine(autoBackup.config, autoBackup.status, autoBackup.running, folderBackups) { config, status, running, files ->
        AutoBackupUi(
            config = config,
            status = status,
            running = running,
            overdue = config.isOn && AutoBackup.isOverdue(
                lastSuccess = status.lastSuccessAt?.let(Instant::ofEpochMilli),
                since = config.enabledAt?.let(Instant::ofEpochMilli),
                now = clock.instant(),
                frequency = config.frequency,
            ),
            folderBackups = files,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // A backup shared to Dakilio replaces whatever is on screen, unless something is running.
        viewModelScope.launch {
            incoming.pending.filterNotNull().collect {
                if (_state.value.isBusy) return@collect
                val uri = incoming.take(dakilio = true) ?: return@collect
                open(uri)
            }
        }
    }

    /** Writes a backup to [uri]; [password] (cleared afterwards) encrypts it. */
    fun export(uri: Uri, password: CharArray?) {
        if (_state.value.isBusy) {
            password?.fill('\u0000')
            return
        }
        job?.cancel()
        repository.close()
        _state.value = BackupUiState.Exporting(0, 0)
        job = viewModelScope.launch {
            _state.value = try {
                BackupUiState.Exported(repository.export(uri, password) { done, total -> _state.update { BackupUiState.Exporting(done, total) } })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Export failed", e)
                BackupUiState.Failed((e as? BackupException)?.reason, BackupStage.Export)
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    fun open(uri: Uri) {
        if (_state.value.isBusy) return
        job?.cancel()
        _state.value = BackupUiState.Reading
        job = viewModelScope.launch {
            _state.value = try {
                when (val result = repository.open(uri)) {
                    BackupOpenResult.NeedsPassword -> BackupUiState.Password(wrong = false)
                    is BackupOpenResult.Ready -> BackupUiState.Preview(result.preview)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e, BackupStage.Open)
            }
        }
    }

    fun unlock(password: CharArray) {
        if (_state.value !is BackupUiState.Password) {
            password.fill('\u0000')
            return
        }
        _state.value = BackupUiState.Unlocking
        job = viewModelScope.launch {
            _state.value = try {
                BackupUiState.Preview(repository.unlock(password))
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                if (e.reason == BackupError.WrongPassword) BackupUiState.Password(wrong = true) else failed(e, BackupStage.Open)
            } catch (e: Exception) {
                failed(e, BackupStage.Open)
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun restore() {
        if (_state.value !is BackupUiState.Preview) return
        _state.value = BackupUiState.Restoring(0, 0)
        job = viewModelScope.launch {
            _state.value = try {
                BackupUiState.Restored(repository.restore { done, total -> _state.update { BackupUiState.Restoring(done, total) } })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Restore failed", e)
                BackupUiState.Failed((e as? BackupException)?.reason, BackupStage.Restore)
            } finally {
                repository.close()
            }
        }
    }

    // Automatic backups.

    fun chooseFolder(treeUri: Uri) {
        viewModelScope.launch {
            try {
                autoBackup.setFolder(treeUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Can't use the folder", e)
            }
        }
    }

    fun setFrequency(frequency: AutoBackupFrequency) {
        viewModelScope.launch { autoBackup.setFrequency(frequency) }
    }

    fun setOnlyWhileCharging(only: Boolean) {
        viewModelScope.launch { autoBackup.setOnlyWhileCharging(only) }
    }

    fun setKeep(keep: Int) {
        viewModelScope.launch { autoBackup.setKeep(keep) }
    }

    /** [password] null removes it; it's cleared either way. */
    fun setAutoPassword(password: CharArray?) {
        viewModelScope.launch { autoBackup.setPassword(password) }
    }

    fun backUpNow() = autoBackup.runNow()

    fun turnOffAutoBackup() {
        viewModelScope.launch { autoBackup.turnOff() }
    }

    /** Back to the start (from a preview, a password prompt, a result or an error). */
    fun reset() {
        if (_state.value.isBusy) return
        job?.cancel()
        repository.close()
        _state.value = BackupUiState.Home
    }

    private fun failed(e: Exception, stage: BackupStage): BackupUiState {
        if (e !is BackupException) Log.w(TAG, "Opening the backup failed", e)
        repository.close()
        return BackupUiState.Failed((e as? BackupException)?.reason, stage)
    }

    override fun onCleared() {
        repository.close()
    }

    private companion object {
        const val TAG = "DakilioBackup"
    }
}
