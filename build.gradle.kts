plugins {
    id("com.android.application") version "8.12.3" apply false
    id("com.android.library") version "8.12.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

// OneDrive turns generated build files into cloud placeholders (ReparsePoints),
// which breaks Gradle snapshotting ("not a regular file"). Keep outputs local.
// CI (Jenkins) : OSG_ANDROID_BUILD_DIR=${WORKSPACE}/.android-build
val externalBuildRoot = System.getenv("OSG_ANDROID_BUILD_DIR")?.let { file(it) }
    ?: file("${System.getProperty("user.home")}/.os-gateway-android-builds/${rootProject.name}")

allprojects {
    layout.buildDirectory.set(externalBuildRoot.resolve(project.name))
}

apply(from = "gradle/ci-signing.gradle.kts")
