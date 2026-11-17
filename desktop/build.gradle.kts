import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(compose.materialIconsExtended)
    // Coil 3 is used by the shared Artwork composable; the desktop app owns
    // its image loader setup, so it needs the dependency on its own classpath.
    implementation(libs.coil.compose3)
    implementation(libs.coil.network.okhttp3)
    implementation(libs.kotlinx.coroutines.swing)
    // LRCLIB responses are parsed with kotlinx.serialization; `:shared` keeps
    // its serialization dependency internal, so the desktop module declares it.
    implementation(libs.kotlinx.serialization.json)
    // Jam room socket (same OkHttp protocol Android uses).
    implementation(libs.okhttp.core)
    // Real local-file decoding for the desktop player: MP3 (mp3spi/jlayer)
    // and Ogg Vorbis (vorbisspi) SPI providers on top of javax.sound.sampled.
    implementation(libs.mp3spi)
    implementation(libs.vorbisspi)
    // Real tag reading (title/artist/album/track/duration) for local files.
    implementation(libs.jaudiotagger)
    // libmpv binding for streaming playback (Opus/AAC/HLS), SimpMusic-style.
    implementation(libs.jna)
    // SimpMusic's desktop streaming method: NewPipe-fork extractors that
    // resolve real googlevideo URLs (signature + n-challenge decoding).
    implementation(libs.pipepipe.extractor)
    implementation(libs.brave.extractor)
    // QR generation for the Android → desktop YouTube session pairing.
    implementation(libs.zxing.core)
    // OS-level media keys (play/pause, next, previous) through a native hook.
    implementation(libs.jnativehook)

    configurations.all {
        // PipePipe bundles protobuf-java (full) while BravePipe brings
        // protobuf-javalite into the same com.google.protobuf namespace;
        // dropping the full variant avoids duplicate classes at runtime.
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
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
            packageVersion = "2.6.5"
            description = "KatMusic - local music and YouTube playback for desktop"
            vendor = "KatMusic"
            copyright = "(c) 2026 KatMusic"

            // The trimmed jlink runtime omits these by default. HTTP clients
            // power lyrics/providers/Last.fm, the embedded callback server
            // authorizes Last.fm, EC crypto is needed for HTTPS handshakes,
            // java.util.prefs stores Desktop settings, and java.xml parses
            // provider responses.
            modules(
                "java.net.http",
                "jdk.httpserver",
                "jdk.crypto.ec",
                "java.prefs",
                "java.xml",
            )

            macOS {
                bundleID = "com.itzfallenme.katmusic"
                dockName = "KatMusic"
            }
            windows {
                menuGroup = "KatMusic"
                // Stable identity so a newer MSI upgrades the existing install
                // instead of creating a second KatMusic entry.
                upgradeUuid = "3f1b5c9e-6d2a-4f7b-9a1c-2e8d4b6f0a13"
                shortcut = true
            }
            linux {
                packageName = "katmusic"
                debMaintainer = "KatMusic <katmusic@users.noreply.github.com>"
                menuGroup = "Audio"
                appCategory = "AudioVideo"
            }
        }
    }
}
