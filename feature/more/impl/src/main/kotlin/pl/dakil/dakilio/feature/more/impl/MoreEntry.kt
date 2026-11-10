package pl.dakil.dakilio.feature.more.impl

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.dakil.dakilio.core.navigation.Navigator
import pl.dakil.dakilio.core.ui.NavTransitions
import pl.dakil.dakilio.feature.archive.api.ArchiveKey
import pl.dakil.dakilio.feature.diaries.api.DiariesKey
import pl.dakil.dakilio.feature.habits.api.HabitsKey
import pl.dakil.dakilio.feature.keywords.api.KeywordsKey
import pl.dakil.dakilio.feature.reminders.api.RemindersKey
import pl.dakil.dakilio.feature.backup.api.BackupKey
import pl.dakil.dakilio.feature.export.api.ExportKey
import pl.dakil.dakilio.feature.importer.api.DaylioImportKey
import pl.dakil.dakilio.feature.lock.api.AppLockKey
import pl.dakil.dakilio.feature.more.api.MoreKey
import pl.dakil.dakilio.feature.settings.api.SettingsKey
import pl.dakil.dakilio.feature.moods.api.MoodsKey
import pl.dakil.dakilio.feature.tags.api.TagsKey

fun EntryProviderScope<NavKey>.moreEntry(navigator: Navigator) {
    entry<MoreKey>(metadata = NavTransitions.topLevel) {
        MoreScreen(
            viewModel = hiltViewModel(),
            onOpenMoods = { navigator.navigate(MoodsKey) },
            onOpenTags = { navigator.navigate(TagsKey) },
            onOpenDiaries = { navigator.navigate(DiariesKey) },
            onOpenSettings = { navigator.navigate(SettingsKey) },
            onOpenImport = { navigator.navigate(DaylioImportKey) },
            onOpenExport = { navigator.navigate(ExportKey) },
            onOpenBackup = { navigator.navigate(BackupKey) },
            onOpenArchive = { navigator.navigate(ArchiveKey) },
            onOpenHabits = { navigator.navigate(HabitsKey) },
            onOpenKeywords = { navigator.navigate(KeywordsKey) },
            onOpenReminders = { navigator.navigate(RemindersKey) },
            onOpenAppLock = { navigator.navigate(AppLockKey) },
        )
    }
}
