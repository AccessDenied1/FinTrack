plugins {
    id("fintrack.android.library")
    id("fintrack.android.hilt")
}

android {
    namespace = "com.sethv.fintrack.core.data"
}

dependencies {
    api(project(":core:model"))
    // api: repository implementations expose Room types (withTransaction) to
    // consumers, and screens may inject the database for bulk maintenance ops.
    api(project(":core:database"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    // withTransaction(...) for atomic bulk-accept across transaction + pending tables.
    implementation(libs.room.ktx)
}
