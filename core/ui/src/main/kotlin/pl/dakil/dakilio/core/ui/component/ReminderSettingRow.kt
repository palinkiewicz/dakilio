package pl.dakil.dakilio.core.ui.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalTime
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Reminder
import pl.dakil.dakilio.core.ui.R
import pl.dakil.dakilio.core.ui.rememberFormats

/**
 * A daily reminder setting: a switch, and while it's on, the time. Turning it on asks for the
 * notification permission first (Android 13+), only then: never at startup. When notifications
 * can't show (denied, or switched off in the system), a line says so with a way to the settings.
 */
@Composable
fun ReminderSettingRow(
    reminder: Reminder,
    onChange: (Reminder) -> Unit,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formats = rememberFormats()
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    // Back from the system settings, or the permission changed elsewhere.
    LifecycleResumeEffect(Unit) {
        allowed = notificationsAllowed(context)
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = notificationsAllowed(context)
        denied = !granted
        if (granted) onChange(reminder.copy(enabled = true))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SwitchListItem(
            title = title,
            body = body,
            symbol = DakilioSymbols.NotificationsActive,
            polygon = MaterialShapes.Cookie9Sided,
            checked = reminder.enabled,
            onCheckedChange = { on ->
                when {
                    !on -> onChange(reminder.copy(enabled = false))
                    needsPermission(context) -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> {
                        denied = false
                        onChange(reminder.copy(enabled = true))
                    }
                }
            },
            index = 0,
            count = if (reminder.enabled) 2 else 1,
        )
        if (reminder.enabled) {
            SegmentedListItem(
                onClick = { pickTime = true },
                shapes = ListItemDefaults.segmentedShapes(1, 2),
                colors = segmentedItemColors(),
                leadingContent = { ShapedIcon(DakilioSymbols.Schedule, polygon = MaterialShapes.Clover4Leaf) },
                trailingContent = { Text(formats.time(reminder.time), style = MaterialTheme.typography.labelLarge) },
            ) { Text(stringResource(R.string.ui_reminder_time)) }
        }
        if (denied || (reminder.enabled && !allowed)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(start = Spacing.l, top = Spacing.xxs),
            ) {
                Text(
                    stringResource(R.string.ui_reminder_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { openNotificationSettings(context) }) { Text(stringResource(R.string.ui_reminder_open_settings)) }
            }
        }
    }

    if (pickTime) {
        val pickerState = rememberTimePickerState(
            initialHour = reminder.time.hour,
            initialMinute = reminder.time.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        TimePickerDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(stringResource(R.string.ui_reminder_pick_time)) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(reminder.copy(time = LocalTime.of(pickerState.hour, pickerState.minute)))
                    pickTime = false
                }) { Text(stringResource(R.string.ui_reminder_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.ui_cancel)) } },
        ) { TimePicker(pickerState) }
    }
}

private fun needsPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

private fun notificationsAllowed(context: Context): Boolean =
    !needsPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
