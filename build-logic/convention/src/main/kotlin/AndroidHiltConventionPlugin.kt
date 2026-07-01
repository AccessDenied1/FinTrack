import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("dagger.hilt.android.plugin")

            val libs = extensions.getByType(
                org.gradle.api.artifacts.VersionCatalogsExtension::class.java
            ).named("libs")

            dependencies.add("implementation", libs.findLibrary("hilt-android").get())
            dependencies.add("ksp", libs.findLibrary("hilt-android-compiler").get())
        }
    }
}
