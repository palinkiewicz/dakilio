package pl.dakil.dakilio.feature.more.impl

import pl.dakil.dakilio.core.model.AutoBackupResult
import pl.dakil.dakilio.core.data.repository.AutoBackupRepository
import pl.dakil.dakilio.core.backup.AutoBackup
import java.time.Instant
import java.time.Duration
import java.time.Clock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.data.repository.HabitRepository
import pl.dakil.dakilio.core.data.repository.KeywordRepository
import pl.dakil.dakilio.core.data.repository.LockRepository
import pl.dakil.dakilio.core.data.repository.PostRepository
import pl.dakil.dakilio.core.data.repository.ReminderRepository
import pl.dakil.dakilio.core.data.repository.SettingsRepository
import pl.dakil.dakilio.core.model.LockSettings

sealed interface AutoBackupRow {
    data object Off : AutoBackupRow
    data object On : AutoBackupRow
    data object Failed : AutoBackupRow
    data object NoneYet : AutoBackupRow
    data class Overdue(val days: Int) : AutoBackupRow
}

@HiltViewModel
class MoreViewModel @Inject constructor(
    postRepository: PostRepository,
    habitRepository: HabitRepository,
    keywordRepository: KeywordRepository,
    reminderRepository: ReminderRepository,
    lockRepository: LockRepository,
    autoBackupRepository: AutoBackupRepository,
    clock: Clock,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    /** Automatic backups, for the Backup row's status: only a problem stands out. */
    val autoBackup: StateFlow<AutoBackupRow> =
        combine(autoBackupRepository.config, autoBackupRepository.status) { config, status ->
            val lastSuccess = status.lastSuccessAt?.let(Instant::ofEpochMilli)
            val since = config.enabledAt?.let(Instant::ofEpochMilli)
            val now = clock.instant()
            when {
                !config.isOn -> AutoBackupRow.Off
                status.lastResult is AutoBackupResult.Failed -> AutoBackupRow.Failed
                AutoBackup.isOverdue(lastSuccess, since, now, config.frequency) ->
                    if (lastSuccess == null) AutoBackupRow.NoneYet else AutoBackupRow.Overdue(Duration.between(lastSuccess, now).toDays().toInt())
                else -> AutoBackupRow.On
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBackupRow.Off)

    /** Habits set up (the Habits extra); 0 means it isn't used yet. */
    val habitCount: StateFlow<Int> =
        habitRepository.habitCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Tags with keywords (the Keywords extra); 0 means it isn't used yet. */
    val keywordTagCount: StateFlow<Int> =
        keywordRepository.keywords.map { keywords -> keywords.map { it.tagId }.distinct().size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Reminders set up (the Reminders extra), on or off; 0 means it isn't used yet. */
    val reminderCount: StateFlow<Int> =
        reminderRepository.count.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The app lock's state, for the App lock row's status. */
    val lock: StateFlow<LockSettings> =
        lockRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockSettings())

    /** Whether to show the "these are samples" hint. */
    val showSampleHint: StateFlow<Boolean> =
        combine(postRepository.samplePostCount, settingsRepository.settings) { count, settings ->
            count > 0 && !settings.sampleHintDismissed
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissSampleHint() {
        viewModelScope.launch { settingsRepository.setSampleHintDismissed(true) }
    }
}
