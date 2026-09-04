plugins {
    id("fintrack.android.library")
    id("fintrack.android.library.compose")
}

android {
    namespace = "com.sethv.fintrack.core.ui"
}

dependencies {
    implementation(project(":core:model"))
    // Format (canonical money formatter) now lives in core:common; exposed
    // to feature modules via this typealias — must be api so the underlying
    // type is visible transitively.
    api(project(":core:common"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
