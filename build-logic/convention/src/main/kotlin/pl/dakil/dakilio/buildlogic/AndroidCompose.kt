package pl.dakil.dakilio.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal fun Project.configureAndroidCompose(extension: CommonExtension) {
    extension.buildFeatures.compose = true
    // AGP's Compose Preview tasks need compiled resources available to host-side tests.
    extension.testOptions.unitTests.isIncludeAndroidResources = true

    dependencies {
        val bom = libs.lib("androidx-compose-bom")
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))
        add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
        add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        // Domain models live in a pure-Kotlin module the Compose compiler can't see; they are
        // immutable by contract, so tell the compiler to treat them (and read-only collections) as stable.
        stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            optIn.addAll(
                "androidx.compose.material3.ExperimentalMaterial3Api",
                "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
                "androidx.compose.animation.ExperimentalSharedTransitionApi",
                "androidx.compose.foundation.ExperimentalFoundationApi",
                "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            )
        }
    }
}
