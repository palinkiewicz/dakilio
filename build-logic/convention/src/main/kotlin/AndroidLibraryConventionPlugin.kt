import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import pl.dakil.dakilio.buildlogic.configureKotlinAndroid
import pl.dakil.dakilio.buildlogic.lib
import pl.dakil.dakilio.buildlogic.libs

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            testOptions.unitTests.isReturnDefaultValues = true
        }
        // Compose modules package Android resources for host tests, which Gradle counts as "test
        // sources"; modules without tests yet must not fail the build for that.
        tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
        dependencies {
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
