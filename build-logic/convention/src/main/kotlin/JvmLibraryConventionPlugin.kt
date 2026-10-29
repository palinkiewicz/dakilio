import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import pl.dakil.dakilio.buildlogic.configureKotlinJvm
import pl.dakil.dakilio.buildlogic.lib
import pl.dakil.dakilio.buildlogic.libs

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureKotlinJvm()
        dependencies {
            add("testImplementation", libs.lib("junit"))
        }
    }
}
