plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.plugins.android.application.toDep())
    compileOnly(libs.plugins.android.library.toDep())
    compileOnly(libs.plugins.kotlin.android.toDep())
    compileOnly(libs.plugins.kotlin.compose.toDep())
    compileOnly(libs.plugins.ksp.toDep())
    compileOnly(libs.plugins.hilt.toDep())
    compileOnly(libs.plugins.room.toDep())
}

fun Provider<PluginDependency>.toDep() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "fintrack.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "fintrack.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "fintrack.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
    }
}
