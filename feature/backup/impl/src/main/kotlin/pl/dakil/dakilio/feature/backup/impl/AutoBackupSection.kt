package pl.dakil.dakilio.feature.backup.impl

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import pl.dakil.dakilio.core.designsystem.component.ConnectedToggleRow
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.ToggleOption
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.AutoBackupConfig
import pl.dakil.dakilio.core.model.AutoBackupFailure
import pl.dakil.dakilio.core.model.AutoBackupFrequency
import pl.dakil.dakilio.core.model.AutoBackupResult
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.component.SwitchListItem
import pl.dakil.dakilio.core.ui.rememberFormats

/** Automatic backups while they're off: what they do, and the way to turn them on. */
@Composable
internal fun AutoBackupOffCard(onChooseFolder: () -> Unit) {
    val haptics = rememberHaptics()
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            CardTitle(DakilioSymbols.Backup, stringResource(R.string.backup_auto_title))
            Text(
                stringResource(R.string.backup_auto_off_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Hint {
                Text(
                    stringResource(R.string.backup_auto_off_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    haptics.confirm()
                    onChooseFolder()
                },
                modifier = Modifier.align(Alignment.End).padding(top = Spacing.xs),
            ) {
                DakilioIcon(DakilioSymbols.Folder, contentDescription = null, modifier = Modifier.padding(end = Spacing.s))
                Text(stringResource(R.string.backup_auto_choose))
            }
        }
    }
}

/** Automatic backups while they're on: how they're doing, then their options as one segmented group. */
@Composable
internal fun ColumnScope.AutoBackupOnSection(
    auto: AutoBackupUi,
    onChooseFolder: () -> Unit,
    onFrequency: (AutoBackupFrequency) -> Unit,
    onCharging: (Boolean) -> Unit,
    onKeep: (Int) -> Unit,
    onPassword: () -> Unit,
    onBackUpNow: () -> Unit,
    onTurnOff: () -> Unit,
) {
    val haptics = rememberHaptics()
    val config = auto.config
    val count = 6
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        AutoBackupStatusBlock(auto)
        SegmentedListItem(
            onClick = onChooseFolder,
            shapes = ListItemDefaults.segmentedShapes(1, count),
            colors = segmentedItemColors(),
            leadingContent = { ShapedIcon(DakilioSymbols.Folder, polygon = MaterialShapes.Square) },
            supportingContent = { Text(config.folderName ?: stringResource(R.string.backup_auto_folder_unknown)) },
            trailingContent = { DakilioIcon(DakilioSymbols.Edit, contentDescription = stringResource(R.string.backup_auto_change_folder)) },
        ) { Text(stringResource(R.string.backup_auto_folder)) }
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(2, count),
            colors = segmentedItemColors(),
            leadingContent = { ShapedIcon(DakilioSymbols.EventRepeat, polygon = MaterialShapes.Cookie6Sided) },
            supportingContent = {
                Column {
                    Text(stringResource(R.string.backup_auto_frequency_body))
                    ConnectedToggleRow(
                        options = listOf(
                            ToggleOption(AutoBackupFrequency.Daily, stringResource(R.string.backup_auto_daily)),
                            ToggleOption(AutoBackupFrequency.Weekly, stringResource(R.string.backup_auto_weekly)),
                        ),
                        selected = config.frequency,
                        onSelect = onFrequency,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                }
            },
        ) { Text(stringResource(R.string.backup_auto_frequency)) }
        SwitchListItem(
            title = stringResource(R.string.backup_auto_charging),
            body = stringResource(R.string.backup_auto_charging_body),
            symbol = DakilioSymbols.BatteryChargingFull,
            polygon = MaterialShapes.Pill,
            checked = config.onlyWhileCharging,
            onCheckedChange = onCharging,
            index = 3,
            count = count,
        )
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(4, count),
            colors = segmentedItemColors(),
            leadingContent = { ShapedIcon(DakilioSymbols.History, polygon = MaterialShapes.Cookie4Sided) },
            supportingContent = {
                Column {
                    Text(stringResource(R.string.backup_auto_keep_body))
                    ConnectedToggleRow(
                        options = AutoBackupConfig.KEEP_CHOICES.map { ToggleOption(it, it.toString()) },
                        selected = config.keep,
                        onSelect = onKeep,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                }
            },
        ) { Text(stringResource(R.string.backup_auto_keep)) }
        SegmentedListItem(
            onClick = onPassword,
            shapes = ListItemDefaults.segmentedShapes(5, count),
            colors = segmentedItemColors(),
            leadingContent = { ShapedIcon(DakilioSymbols.Lock, polygon = MaterialShapes.Clover4Leaf) },
            supportingContent = {
                Text(stringResource(if (config.hasPassword) R.string.backup_auto_password_on else R.string.backup_auto_password_off))
            },
            trailingContent = { DakilioIcon(DakilioSymbols.ChevronRight, contentDescription = null) },
        ) { Text(stringResource(R.string.backup_auto_password)) }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onTurnOff) { Text(stringResource(R.string.backup_auto_turn_off)) }
        FilledTonalButton(
            onClick = {
                haptics.confirm()
                onBackUpNow()
            },
            enabled = auto.running == null,
        ) {
            DakilioIcon(DakilioSymbols.Backup, contentDescription = null, modifier = Modifier.padding(end = Spacing.s))
            Text(stringResource(R.string.backup_auto_now))
        }
    }
}

/** The first tile of the group: running, failed, overdue, waiting or up to date. */
@Composable
private fun AutoBackupStatusBlock(auto: AutoBackupUi) {
    val context = LocalContext.current
    val status = auto.status
    val config = auto.config
    val failure = (status.lastResult as? AutoBackupResult.Failed)?.reason
    val warning = auto.running == null && (failure != null || auto.overdue)
    val title: String
    val body: String
    when {
        auto.running != null -> {
            title = stringResource(R.string.backup_auto_running)
            val (done, total) = auto.running
            body = if (total > 0 && done < total) {
                stringResource(R.string.backup_exporting_photos, done, total)
            } else {
                stringResource(R.string.backup_exporting_writing)
            }
        }
        failure != null -> {
            title = stringResource(R.string.backup_auto_failed_title)
            body = stringResource(
                when (failure) {
                    AutoBackupFailure.FolderGone -> R.string.backup_auto_failed_folder
                    AutoBackupFailure.NoSpace -> R.string.backup_auto_failed_space
                    AutoBackupFailure.PasswordUnavailable -> R.string.backup_auto_failed_password
                    AutoBackupFailure.Other -> R.string.backup_auto_failed_other
                },
            )
        }
        auto.overdue -> {
            val since = status.lastSuccessAt ?: config.enabledAt ?: 0L
            val days = Duration.between(Instant.ofEpochMilli(since), Instant.now()).toDays().toInt()
            title = if (status.lastSuccessAt == null) {
                stringResource(R.string.backup_auto_none_yet)
            } else {
                pluralStringResource(R.plurals.backup_auto_overdue_title, days, days)
            }
            body = stringResource(if (config.onlyWhileCharging) R.string.backup_auto_overdue_charging else R.string.backup_auto_overdue_battery)
        }
        status.lastSuccessAt == null -> {
            title = stringResource(R.string.backup_auto_waiting_title)
            body = stringResource(if (config.onlyWhileCharging) R.string.backup_auto_waiting_charging else R.string.backup_auto_waiting_battery)
        }
        else -> {
            title = stringResource(R.string.backup_auto_up_to_date)
            val newest = auto.folderBackups.firstOrNull()
            val time = newest?.time ?: LocalDateTime.ofInstant(Instant.ofEpochMilli(status.lastSuccessAt ?: 0L), ZoneId.systemDefault())
            val bytes = newest?.bytes ?: status.bytes
            val counts = stringResource(
                R.string.backup_auto_counts,
                pluralStringResource(R.plurals.backup_count_entries, status.entries, status.entries),
                pluralStringResource(R.plurals.backup_count_photos, status.photos, status.photos),
            )
            val last = if (bytes > 0) {
                stringResource(R.string.backup_auto_last, whenText(time), Formatter.formatShortFileSize(context, bytes))
            } else {
                stringResource(R.string.backup_auto_last_no_size, whenText(time))
            }
            body = "$last\n$counts"
        }
    }
    val containerColor = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(0, 6),
        colors = ListItemDefaults.segmentedColors(containerColor = containerColor, contentColor = contentColor),
        leadingContent = {
            val (symbol, polygon) = when {
                auto.running != null -> DakilioSymbols.Backup to MaterialShapes.Cookie9Sided
                warning -> DakilioSymbols.Warning to MaterialShapes.Triangle
                else -> DakilioSymbols.CheckCircle to MaterialShapes.Sunny
            }
            ShapedIcon(
                symbol,
                polygon = polygon,
                size = 48.dp,
                containerColor = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (warning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        supportingContent = {
            Column {
                Text(body)
                val expand = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
                AnimatedVisibility(auto.running != null, enter = expandVertically(expand) + fadeIn(), exit = shrinkVertically(expand) + fadeOut()) {
                    val running = auto.running
                    if (running != null && running.total > 0) {
                        LinearProgressIndicator(
                            progress = { running.done.toFloat() / running.total },
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.s))
                    }
                }
            }
        },
    ) { Text(title, style = MaterialTheme.typography.titleMediumEmphasized) }
}

/** "today, 03:12", "yesterday, 03:12", "8 Nov, 03:12" or, in another year, "8 Nov 2025, 03:12". */
@Composable
internal fun whenText(time: LocalDateTime): String {
    val formats = rememberFormats()
    val today = LocalDate.now()
    val date = time.toLocalDate()
    val clock = formats.time(time.toLocalTime())
    return when {
        date == today -> stringResource(R.string.backup_today_at, clock)
        date == today.minusDays(1) -> stringResource(R.string.backup_yesterday_at, clock)
        date.year == today.year -> stringResource(R.string.backup_date_at, formats.dayMonth(date), clock)
        else -> stringResource(R.string.backup_date_at, formats.dayMonthYear(date), clock)
    }
}

/** Sets, changes or removes the automatic backups' password. */
@Composable
internal fun AutoBackupPasswordSheet(hasPassword: Boolean, onSave: (CharArray?) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    // Not saveable on purpose: passwords never go into the saved instance state.
    val password = remember { TextFieldState() }
    val repeat = remember { TextFieldState() }
    DisposableEffect(Unit) {
        onDispose {
            password.clearText()
            repeat.clearText()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(bottom = Spacing.l).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(R.string.backup_auto_password_title), style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                stringResource(R.string.backup_auto_password_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.s),
            )
            PasswordField(password, stringResource(R.string.backup_password))
            val mismatch = repeat.text.isNotEmpty() && password.text.toString() != repeat.text.toString()
            PasswordField(
                repeat,
                stringResource(R.string.backup_password_repeat),
                isError = mismatch,
                supportingText = if (mismatch) stringResource(R.string.backup_password_mismatch) else null,
            )
            Row(verticalAlignment = Alignment.Top) {
                DakilioIcon(DakilioSymbols.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.backup_password_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
            ) {
                if (hasPassword) {
                    TextButton(
                        onClick = { onSave(null) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.backup_auto_password_remove)) }
                }
                val ready = password.text.isNotEmpty() && password.text.toString() == repeat.text.toString()
                Button(
                    onClick = {
                        haptics.confirm()
                        onSave(password.text.let { text -> CharArray(text.length) { text[it] } })
                    },
                    enabled = ready,
                ) { Text(stringResource(R.string.backup_auto_password_save)) }
            }
        }
    }
}

@Composable
internal fun TurnOffAutoBackupDialog(folderName: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DakilioIcon(DakilioSymbols.Backup, contentDescription = null) },
        title = { Text(stringResource(R.string.backup_auto_turn_off_title)) },
        text = {
            Text(
                if (folderName != null) {
                    stringResource(R.string.backup_auto_turn_off_body, folderName)
                } else {
                    stringResource(R.string.backup_auto_turn_off_body_unnamed)
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_auto_turn_off)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_confirm_no)) } },
    )
}

/** A recent automatic backup in the Restore card. */
@Composable
internal fun FolderBackupRow(time: LocalDateTime, bytes: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            DakilioIcon(DakilioSymbols.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                whenText(time).replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(start = Spacing.m),
            )
            if (bytes > 0) {
                Text(
                    Formatter.formatShortFileSize(context, bytes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
