import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // The iOS app shell in `iosApp` hosts the exact same Compose UI through
    // the framework below. Kotlin/Native targets compile the shared screens,
    // view models and models for iPhone, iPad and the simulator.
    //
    // iosX64 is intentionally absent: the JetBrains navigation/lifecycle
    // artifacts this UI uses no longer publish an x86_64 iOS variant. Device
    // builds (iosArm64) and Apple Silicon simulators (iosSimulatorArm64) are
    // fully supported.
    iosArm64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        compilerOptions {
            // UIKit/AVFoundation/F filesystem calls are C interop by nature.
            optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
        }
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil.compose3)
            implementation(libs.androidx.navigation.compose.multiplatform)
            implementation(libs.androidx.lifecycle.viewmodel.compose.multiplatform)
            implementation(libs.androidx.lifecycle.runtime.compose.multiplatform)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            // Coil 3 network fetching stays on the JVM/Android side; iOS loads
            // local artwork only (the iOS build has no online catalogue).
            implementation(libs.coil.network.okhttp3)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.coil.network.okhttp3)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.itzfallenme.katmusic.shared.resources"
}

android {
    namespace = "com.itzfallenme.katmusic.shared"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
