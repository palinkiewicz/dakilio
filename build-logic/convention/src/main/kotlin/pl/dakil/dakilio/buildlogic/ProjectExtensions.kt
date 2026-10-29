package pl.dakil.dakilio.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.intVersion(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()

fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()
