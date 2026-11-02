package pl.dakil.dakilio

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import pl.dakil.dakilio.core.designsystem.theme.DakilioTheme
import pl.dakil.dakilio.core.model.ThemeMode
import pl.dakil.dakilio.core.ui.LocalEntryDisplay
import pl.dakil.dakilio.core.ui.LocalShowHints
import pl.dakil.dakilio.feature.importer.impl.IncomingBackups
import javax.inject.Inject
import pl.dakil.dakilio.ui.DakilioApp
import pl.dakil.dakilio.ui.IntroFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var incomingBackups: IncomingBackups

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // On recreation the intent is the old one, already handled.
        if (savedInstanceState == null) incomingBackups.deliver(intent)
        setContent {
            val incomingBackup by incomingBackups.pending.collectAsStateWithLifecycle()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val showHints by viewModel.showHints.collectAsStateWithLifecycle()
            val entryDisplay by viewModel.entryDisplay.collectAsStateWithLifecycle()
            val showIntro by viewModel.showIntro.collectAsStateWithLifecycle()
            val mode = themeMode ?: return@setContent
            val intro = showIntro ?: return@setContent
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
                CompositionLocalProvider(LocalShowHints provides showHints, LocalEntryDisplay provides entryDisplay) {
                    if (intro) {
                        IntroFlow(hasIncomingBackup = incomingBackup != null, onFinished = viewModel::finishIntro)
                    } else {
                        DakilioApp(hasIncomingBackup = incomingBackup != null)
                    }
                }
            }
        }
    }

    // singleTask: a backup forwarded by BackupReceiverActivity comes back to this instance, not a new one.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (incomingBackups.deliver(intent)) setIntent(intent)
    }
}
