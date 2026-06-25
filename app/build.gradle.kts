plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    configurations.all {
        exclude(group = "com.thingclips.smart", module = "thingsmart-modularCampAnno")
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

        // The Tuya/Thing SDK only ships native libraries (incl. libthing_security.so)
        // for ARM. Limit ABIs so we never package a broken x86/x86_64 variant.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // BizBundle dependencies bundle conflicting META-INF metadata files.
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/*.version"
            )
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

    // Tuya UI BizBundle: standard device control panel. The BOM aligns all bizbundle
    // artifacts (and the core SDK) to one version.
    implementation(platform(libs.thing.bizbundle.bom))
    implementation(libs.thing.bizbundle.panel)
    implementation(libs.thing.bizbundle.panelmore)
    implementation(libs.thing.bizbundle.basekit)
    implementation(libs.thing.bizbundle.bizkit)
    implementation(libs.thing.bizbundle.devicekit)
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