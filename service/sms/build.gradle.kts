plugins {
    id("fintrack.android.library")
    id("fintrack.android.hilt")
}

android {
    namespace = "com.sethv.fintrack.service.sms"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":service:parser"))
    implementation(project(":service:categorizer"))
    implementation(project(":service:notification"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
