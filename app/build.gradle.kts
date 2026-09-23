import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// API keys live in local.properties (never commit that file):
//   MAPS_API_KEY=AIza...
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val mapsKey: String = localProps.getProperty("MAPS_API_KEY", "")

android {
    namespace = "com.kl.travel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kl.travel"
        minSdk = 26
        targetSdk = 35
        // One number to bump per change; also update CHANGELOG.md (a unit test checks they match).
        val appVersion = "1.25.3"
        versionCode = appVersion.split(".").let { it[0].toInt() * 10000 + it[1].toInt() * 100 + it[2].toInt() }
        versionName = appVersion
        // Phones only (skips x86 emulators). The receipt-reading model is big per CPU type.
        ndk { abiFilters += listOf("arm64-v8a") }   // 64-bit phones only (every phone since ~2019): keeps the APK small enough to download on mobile data
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsKey\"")
    }

    buildTypes {
        // The build we hand out: shrunk (R8) so the download is small, signed with the same debug key so it installs over older versions.
        create("small") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // Compress the native libraries inside the APK so the download is smaller.
    packaging { jniLibs { useLegacyPackaging = true } }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // On-device receipt text recognition (model is bundled, so it works with no signal).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("com.google.maps.android:maps-compose:6.2.1")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.json:json:20240303")   // real org.json for JVM tests (the Android stub is empty)
}
