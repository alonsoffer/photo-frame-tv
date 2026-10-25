import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Read OAuth client credentials from local.properties (kept out of git).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String, default: String = ""): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: default)

// Release signing config (upload key for Play App Signing). Kept out of git.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.photoframe.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.photoframe.tv"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"

        // Demo mode shows sample photos without needing a Google account. Toggle in local.properties.
        buildConfigField("boolean", "DEMO_MODE", prop("DEMO_MODE", "true"))
        // TV/limited-input device-flow OAuth client (device authorization grant).
        buildConfigField("String", "TV_CLIENT_ID", "\"${prop("TV_CLIENT_ID")}\"")
        buildConfigField("String", "TV_CLIENT_SECRET", "\"${prop("TV_CLIENT_SECRET")}\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Compose + TV
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3") // touch/phone management UI
    implementation("androidx.tv:tv-material:1.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // QR scanner for pairing a TV from the phone
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Images (Coil 2.x embeds OkHttp; we hand it our authenticated client)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // QR code generation for the TV pairing screen
    implementation("com.google.zxing:core:3.5.3")

    // Storage
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
