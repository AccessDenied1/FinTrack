plugins {
    id("fintrack.android.library")
    id("fintrack.android.hilt")
}

android {
    namespace = "com.sethv.fintrack.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
