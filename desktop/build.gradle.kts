import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    // Coil 3 is used by the shared Artwork composable; the desktop app owns
    // its image loader setup, so it needs the dependency on its own classpath.
    implementation(libs.coil.compose3)
    implementation(libs.coil.network.okhttp3)
    implementation(libs.kotlinx.coroutines.swing)
    // Real local-file decoding for the desktop player: MP3 (mp3spi/jlayer)
    // and Ogg Vorbis (vorbisspi) SPI providers on top of javax.sound.sampled.
    implementation(libs.mp3spi)
    implementation(libs.vorbisspi)
    // Real tag reading (title/artist/album/track/duration) for local files.
    implementation(libs.jaudiotagger)
}

compose.desktop {
    application {
        mainClass = "com.itzfallenme.katmusic.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "KatMusic"
            packageVersion = "2.6.4"
        }
    }
}
