plugins {
    id("fintrack.android.library")
    id("fintrack.android.hilt")
}

android {
    namespace = "com.sethv.fintrack.service.categorizer"
}

dependencies {
    implementation(project(":core:model"))
    testImplementation(libs.junit)
}
