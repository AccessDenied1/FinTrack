plugins {
    id("fintrack.android.library")
    id("fintrack.android.hilt")
}

android {
    namespace = "com.sethv.fintrack.service.parser"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
