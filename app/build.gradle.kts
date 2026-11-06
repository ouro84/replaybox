plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
}

// Single source of truth for the version: gradle.properties `replaybox.version`
// (semver X.Y.Z, tagged vX.Y.Z by scripts/release.ps1). versionCode is derived
// so it always grows with the semver: X*10000 + Y*100 + Z.
val replayboxVersionName: String = providers.gradleProperty("replaybox.version").get()
val replayboxVersionCode: Int = replayboxVersionName.split(".").let { parts ->
    require(parts.size == 3 && parts.all { p -> p.toIntOrNull()?.let { it in 0..99 } == true }) {
        "replaybox.version must be X.Y.Z with each part in 0..99, got $replayboxVersionName"
    }
    parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
}

// Nightly health-check (SPEC §CI): real network, so its sources
// (src/healthCheck) are compiled and run only with -PhealthCheck, never as
// part of the unit test suite (AGENTS.md: zero network in unit tests).
val healthCheck: Boolean = providers.gradleProperty("healthCheck").isPresent

android {
    namespace = "com.replaybox"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.replaybox"
        minSdk = 26
        targetSdk = 34
        versionCode = replayboxVersionCode
        versionName = replayboxVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // V1: release is signed with the debug keystore of the release machine
            // (~/.android/debug.keystore) so it updates a sideloaded debug build and
            // vice versa — same certificate on the box, no secret in the repo.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }
    packagingOptions {
        resources.excludes.add("META-INF/{AL2.0,LGPL2.1}")
    }
    if (healthCheck) {
        sourceSets.getByName("test").java.srcDir("src/healthCheck/java")
    }
}

tasks.withType<Test>().configureEach {
    if (healthCheck) {
        filter.includeTestsMatching("com.replaybox.healthcheck.*")
        // Real network: never up-to-date, never served from the build cache.
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
        systemProperty("healthcheck.out", layout.buildDirectory.dir("healthcheck").get().asFile.path)
        testLogging {
            events("passed", "failed")
            showStandardStreams = true
        }
    } else {
        // Classes compiled by an earlier -PhealthCheck run can linger in the
        // test output dir: never let the unit suite execute them.
        filter.excludeTestsMatching("com.replaybox.healthcheck.*")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    implementation(platform("androidx.compose:compose-bom:2023.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha07")
    implementation("androidx.tv:tv-material:1.0.0-alpha07")

    implementation("io.coil-kt:coil-compose:2.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    implementation("androidx.media3:media3-exoplayer:1.1.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.1.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.1.1")
    implementation("androidx.media3:media3-ui:1.1.1")
    implementation("androidx.media3:media3-session:1.1.1")

    implementation("androidx.room:room-runtime:2.5.1")
    implementation("androidx.room:room-ktx:2.5.1")
    kapt("androidx.room:room-compiler:2.5.1")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.1.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
}
