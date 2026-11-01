pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Dakilio"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":baselineprofile")

include(":core:model")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":core:navigation")
include(":core:daylio")

listOf("timeline", "search", "entry", "insights", "more", "moods", "tags", "diaries", "settings", "importer").forEach { feature ->
    include(":feature:$feature:api")
    include(":feature:$feature:impl")
}
