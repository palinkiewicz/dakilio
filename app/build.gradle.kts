import java.util.Properties

plugins {
    alias(libs.plugins.dakilio.android.application)
    alias(libs.plugins.dakilio.android.application.compose)
    alias(libs.plugins.dakilio.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

// Release credentials stay out of the repo: drop a keystore.properties next to settings.gradle.kts
// (storeFile, storePassword, keyAlias, keyPassword) to sign release/preview builds with a real key.
val keystoreProperties: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "pl.dakil.dakilio"

    defaultConfig {
        applicationId = "pl.dakil.dakilio"
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["appName"] = "Dakilio"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            manifestPlaceholders["appName"] = "Dakilio (DEBUG)"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Release-identical (R8, shrinking, not debuggable) but installable next to release and debug:
        // the build to hand to testers and to smoke-test R8 with.
        // Build types the baseline-profile plugin synthesizes (nonMinifiedRelease, benchmarkRelease) are
        // local measuring tools only: sign them with the debug key so they install on test devices.
        matching { it.name.startsWith("nonMinified") || it.name.startsWith("benchmark") }.configureEach {
            signingConfig = signingConfigs.getByName("debug")
        }
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-PREVIEW"
            manifestPlaceholders["appName"] = "Dakilio (PREVIEW)"
            matchingFallbacks += "release"
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    androidResources {
        localeFilters += "en"
    }

    // No Google-encrypted dependency blob in the APK (F-Droid reproducibility, privacy).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/**/LICENSE*",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
    // One profile for every variant (generated against `preview`, which is release-identical).
    mergeIntoMain = true
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.navigation)

    implementation(projects.feature.timeline.impl)
    implementation(projects.feature.search.impl)
    implementation(projects.feature.entry.impl)
    implementation(projects.feature.insights.impl)
    implementation(projects.feature.more.impl)
    implementation(projects.feature.moods.impl)
    implementation(projects.feature.tags.impl)
    implementation(projects.feature.diaries.impl)
    implementation(projects.feature.settings.impl)
    implementation(projects.feature.importer.impl)
    implementation(projects.feature.archive.impl)
    implementation(projects.feature.intro.impl)
    implementation(projects.feature.extras.impl)
    implementation(projects.feature.tagcolors.impl)
    implementation(projects.feature.habits.impl)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)

    baselineProfile(projects.baselineprofile)
}
