plugins {
    alias(libs.plugins.dakilio.android.feature)
}

android {
    namespace = "pl.dakil.dakilio.feature.more.impl"
}

dependencies {
    api(projects.feature.more.api)
    implementation(projects.feature.moods.api)
    implementation(projects.feature.tags.api)
    implementation(projects.feature.diaries.api)
    implementation(projects.feature.settings.api)
    implementation(projects.feature.importer.api)
    implementation(projects.feature.archive.api)
    implementation(projects.feature.extras.api)
}
