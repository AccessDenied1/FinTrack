plugins {
    id("fintrack.android.library")
    id("fintrack.android.hilt")
    alias(libs.plugins.room)
}

android {
    namespace = "com.sethv.fintrack.core.database"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.core)
}

room {
    schemaDirectory("$projectDir/schemas")
}
