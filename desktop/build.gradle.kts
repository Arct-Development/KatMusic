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
    // Coil 3 is used by the shared Artwork composable; the desktop app owns
    // its image loader setup, so it needs the dependency on its own classpath.
    implementation(libs.coil.compose3)
    implementation(libs.coil.network.okhttp3)
    implementation(libs.kotlinx.coroutines.swing)
    // LRCLIB responses are parsed with kotlinx.serialization; `:shared` keeps
    // its serialization dependency internal, so the desktop module declares it.
    implementation(libs.kotlinx.serialization.json)
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
            packageVersion = "2.6.4"
        }
    }
}
