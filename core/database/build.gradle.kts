plugins {
    id("fintrack.android.library")
    id("fintrack.android.hilt")
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

// NOTE: Room schema export is disabled (see @Database exportSchema=false).
// The androidx.room Gradle plugin passes the absolute schema directory to KSP1
// as an apoption, and KSP1 rejects any apoption value containing whitespace
// ("KSP apoption does not match \S+=\S+"). This project's checkout path
// ("…\vipin's Space\…") contains a space, so schema export cannot work under
// KSP1 here. KSP2 sidesteps the apoption check but Room 2.6.1's processor is
// not KSP2-compatible (fails with [MissingType]). To re-enable schema export,
// move the repo to a space-free path OR upgrade to a Room version with working
// KSP2 support, then restore `alias(libs.plugins.room)` + the room{} block and
// set exportSchema = true.
