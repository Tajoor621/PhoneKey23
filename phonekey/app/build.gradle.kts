plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// compileSdk 37 looks up platforms/android-37. sdkmanager only publishes
// platforms;android-37.0. Alias it before the Android plugin resolves the SDK.
// The new AGP DSL that names minorApiLevel stays off with android.newDsl=false.
fun linkAndroid37Platform() {
    val sdkRoot = System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_HOME")
        ?: run {
            val local = rootProject.file("local.properties")
            if (!local.isFile) return
            val props = java.util.Properties()
            local.inputStream().use { props.load(it) }
            props.getProperty("sdk.dir")?.takeIf { it.isNotBlank() } ?: return
        }
    val platforms = java.nio.file.Path.of(sdkRoot, "platforms")
    val installed = platforms.resolve("android-37.0")
    val alias = platforms.resolve("android-37")
    if (java.nio.file.Files.isSymbolicLink(alias) || java.nio.file.Files.isDirectory(alias)) return
    if (!java.nio.file.Files.isDirectory(installed)) return
    java.nio.file.Files.createSymbolicLink(alias, java.nio.file.Path.of("android-37.0"))
}

linkAndroid37Platform()

android {
    namespace = "com.phonekey.vpn"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.phonekey.vpn"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = "1.2.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("KEYSTORE_FILE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val storePath = System.getenv("KEYSTORE_FILE")
            if (!storePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        lintConfig = file("lint.xml")
        abortOnError = true
        warningsAsErrors = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }
}

dependencies {
    // Compose 1.12 needs compileSdk 37 and AGP 9.1, which this module now uses.
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Latest tunnel artifact published to Maven Central (verified 2026-11-11).
    // com.wireguard.config.Interface parses IncludedApplications and ExcludedApplications.
    // There is no newer com.wireguard.android:tunnel release than 1.0.20260102.
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.code.gson:gson:2.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
