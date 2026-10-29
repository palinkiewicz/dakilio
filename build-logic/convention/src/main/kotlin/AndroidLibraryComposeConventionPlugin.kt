import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import pl.dakil.dakilio.buildlogic.configureAndroidCompose

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dakilio.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        configureAndroidCompose(extensions.getByType<LibraryExtension>())
    }
}
