import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Tuya credentials are kept out of source control. They're read from an environment
// variable (used by CI) or, failing that, from local.properties (used for local dev).
// Neither is committed — see local.properties.example for the keys to set.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(name: String): String =
    System.getenv(name) ?: localProperties.getProperty(name) ?: ""

android {
    configurations.all {
        exclude(group = "com.thingclips.smart", module = "thingsmart-modularCampAnno")
        // The IPC SDK pulls Fresco 3.1.x, whose native libs are 4 KB-aligned and crash on
        // 16 KB-page devices. Force Fresco >= 3.6.0 (16 KB-aligned .so files).
        resolutionStrategy.eachDependency {
            if (requested.group == "com.facebook.fresco") useVersion("3.6.0")
        }
    }
    namespace = "com.shaqsid.smart"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shaqsid.smart"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Tuya appKey/appSecret, injected from env (CI) or local.properties (dev). Exposed to
        // Kotlin via BuildConfig and to AndroidManifest via manifest placeholders (the SDK reads
        // both). Empty when unset so the build doesn't fail; the app just can't reach Tuya.
        val thingAppKey = secret("THING_APP_KEY")
        val thingAppSecret = secret("THING_APP_SECRET")
        buildConfigField("String", "THING_APP_KEY", "\"$thingAppKey\"")
        buildConfigField("String", "THING_APP_SECRET", "\"$thingAppSecret\"")
        manifestPlaceholders["THING_SMART_APPKEY"] = thingAppKey
        manifestPlaceholders["THING_SMART_SECRET"] = thingAppSecret

        // The Tuya/Thing SDK only ships native libraries (incl. libthing_security.so)
        // for ARM. Limit ABIs so we never package a broken x86/x86_64 variant.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        // Sign debug builds with an explicit keystore so local and CI builds share one signature
        // (APKs upgrade in place). Relying on the default ~/.android/debug.keystore is unreliable
        // on CI, where the plugin resolves a different location and generates a throwaway key.
        // Path comes from DEBUG_KEYSTORE_PATH (set in CI); locally it falls back to the standard
        // debug keystore, whose SHA-256 is the one registered on the Tuya platform.
        getByName("debug") {
            val keystorePath = secret("DEBUG_KEYSTORE_PATH")
                .ifBlank { "${System.getProperty("user.home")}/.android/debug.keystore" }
            val keystoreFile = file(keystorePath)
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += "lib/*/libc++_shared.so"
            // The Tuya security component loads its .so from disk, so the native libs
            // must be extracted on install (extractNativeLibs=true) rather than kept
            // compressed inside the APK. Otherwise SecureNativeApi fails to link.
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Tuya / Thing Smart
    implementation(libs.thing.home.sdk)
    implementation(libs.fastjson)
    implementation(libs.okhttp.urlconnection)
    // IP camera (IPC) SDK for live preview / P2P video.
    implementation(libs.thing.ipc.sdk)
    // App-specific "security algorithm" component (security-algorithm.aar), downloaded from the
    // Tuya IoT Platform for this appKey + package + SHA-256. Provides libthing_security_algorithm.so
    // which libthing_security.so depends on. Drop the .aar into app/libs/.
    implementation(fileTree("libs") { include("*.jar", "*.aar") })

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}