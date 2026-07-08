plugins {
    id("fintrack.android.library")
    id("fintrack.android.hilt")
}

android {
    namespace = "com.sethv.fintrack.core.data"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    // withTransaction(...) for atomic bulk-accept across transaction + pending tables.
    implementation(libs.room.ktx)
}
