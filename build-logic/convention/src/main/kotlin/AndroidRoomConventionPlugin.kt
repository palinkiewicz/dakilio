import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import pl.dakil.dakilio.buildlogic.lib
import pl.dakil.dakilio.buildlogic.libs

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("androidx.room")
        pluginManager.apply("com.google.devtools.ksp")
        extensions.configure<RoomExtension> {
            // Schemas are committed so every migration is reviewable in the diff.
            schemaDirectory("$projectDir/schemas")
        }
        dependencies {
            add("implementation", libs.lib("androidx-room-runtime"))
            add("implementation", libs.lib("androidx-room-ktx"))
            add("ksp", libs.lib("androidx-room-compiler"))
        }
    }
}
