plugins {
    alias(libs.plugins.dakilio.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.serialization.json)
}
