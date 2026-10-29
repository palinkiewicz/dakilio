import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import pl.dakil.dakilio.buildlogic.lib
import pl.dakil.dakilio.buildlogic.libs

/** A feature's `impl` module: Compose UI + Hilt ViewModels + Navigation 3 entries. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dakilio.android.library.compose")
        pluginManager.apply("dakilio.hilt")
        dependencies {
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:navigation"))
            add("implementation", project(":core:data"))
            add("implementation", libs.lib("androidx-compose-material3"))
            add("implementation", libs.lib("androidx-navigation3-runtime"))
            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
        }
    }
}
