plugins {
    id("fintrack.android.library")
    id("fintrack.android.hilt")
}

android {
    namespace = "com.sethv.fintrack.core.database"
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.sqlite:sqlite-framework:2.4.0")
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
