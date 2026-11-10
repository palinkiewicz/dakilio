package pl.dakil.dakilio

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import pl.dakil.dakilio.core.designsystem.theme.DakilioTheme
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.ui.LocalEntryDisplay
import pl.dakil.dakilio.core.ui.LocalFirstDayOfWeek
import pl.dakil.dakilio.core.ui.LocalShowHints
import pl.dakil.dakilio.core.data.repository.AppLock
import pl.dakil.dakilio.core.data.repository.IncomingBackups
import pl.dakil.dakilio.core.notifications.ReminderLaunches
import pl.dakil.dakilio.core.notifications.ReminderSync
import pl.dakil.dakilio.core.autobackup.AutoBackupSync
import pl.dakil.dakilio.feature.lock.impl.LockGate
import javax.inject.Inject
import pl.dakil.dakilio.ui.DakilioApp
import pl.dakil.dakilio.ui.IntroFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var incomingBackups: IncomingBackups

    @Inject lateinit var appLock: AppLock

    @Inject lateinit var reminderLaunches: ReminderLaunches

    @Inject lateinit var reminderSync: ReminderSync

    @Inject lateinit var autoBackupSync: AutoBackupSync

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // On recreation the intent is the old one, already handled.
        if (savedInstanceState == null) {
            incomingBackups.deliver(intent)
            reminderLaunches.deliver(intent)
        }
        setContent {
            val incomingBackup by incomingBackups.pending.collectAsStateWithLifecycle()
            val reminderLaunch by reminderLaunches.pending.collectAsStateWithLifecycle()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val showHints by viewModel.showHints.collectAsStateWithLifecycle()
            val entryDisplay by viewModel.entryDisplay.collectAsStateWithLifecycle()
            val firstDayOfWeek by viewModel.firstDayOfWeek.collectAsStateWithLifecycle()
            val showIntro by viewModel.showIntro.collectAsStateWithLifecycle()
            val locked by viewModel.locked.collectAsStateWithLifecycle()
            val recovered by viewModel.recovered.collectAsStateWithLifecycle()
            val secureScreen by viewModel.secureScreen.collectAsStateWithLifecycle()
            val mode = themeMode ?: return@setContent
            val intro = showIntro ?: return@setContent
            val isLocked = locked ?: return@setContent
            DisposableEffect(secureScreen) {
                if (secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { }
            }
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            DakilioTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalShowHints provides showHints,
                    LocalEntryDisplay provides entryDisplay,
                    LocalFirstDayOfWeek provides firstDayOfWeek,
                ) {
                    LockGate(locked = isLocked) {
                        if (intro) {
                            IntroFlow(incomingBackup = incomingBackup, onFinished = viewModel::finishIntro)
                        } else {
                            // Past the intro the database may exist, so reminders can follow it.
                            LaunchedEffect(Unit) {
                                reminderSync.start()
                                autoBackupSync.start()
                            }
                            DakilioApp(
                                incomingBackup = incomingBackup,
                                openAppLock = recovered,
                                reminderLaunch = reminderLaunch,
                                onReminderLaunched = reminderLaunches::consume,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onForegrounded()
    }

    override fun onStop() {
        super.onStop()
        // A rotation or a language switch recreates the activity; that isn't leaving the app.
        if (!isChangingConfigurations) appLock.onBackgrounded()
    }

    /**
     * Every ActivityResult launch (the Photo Picker, opening and saving files) comes through here.
     * Coming back from one of those isn't coming back from somewhere else, so it doesn't lock the app.
     */
    @Deprecated("Overridden only to notice the app's own pickers; still delegates.")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        if (requestCode >= 0) appLock.expectReturnFromPicker()
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    // singleTask: a backup forwarded by BackupReceiverActivity comes back to this instance, not a new one.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (incomingBackups.deliver(intent) || reminderLaunches.deliver(intent)) setIntent(intent)
    }
}
