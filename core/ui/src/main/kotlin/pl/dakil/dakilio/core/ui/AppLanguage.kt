package pl.dakil.dakilio.core.ui

import android.app.LocaleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.util.Locale

/** The languages the UI is translated into. Keep in sync with `localeFilters` and `locales_config.xml`. */
val AppLanguages: List<String> = listOf("en", "pl")

/** The language the UI is actually shown in, which isn't the device's when there's no translation for it. */
@Composable
fun currentAppLanguage(): String = stringResource(R.string.ui_format_language)

/** A language named in itself, the way language pickers list it ("Polski"). */
fun languageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

/** Per-app languages need Android 13. Older versions always follow the device language. */
@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
val canChooseAppLanguage: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Switches the app's language. The system then recreates the activity in it. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun setAppLanguage(context: Context, tag: String) {
    context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
}

/** Opens the system's language screen for this app. Returns false if it couldn't be started. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun openAppLanguageSettings(context: Context): Boolean = try {
    context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    true
} catch (_: ActivityNotFoundException) {
    false
}
