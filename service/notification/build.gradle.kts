plugins {
    id("fintrack.android.library")
    id("fintrack.android.hilt")
}

android {
    namespace = "com.sethv.fintrack.service.notification"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
}
