plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.kotlin.android") version "2.1.0"
}

android {
    namespace = "com.kashif.folar"
    compileSdk = 35
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "com.kashif.folar.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.add("arm64-v8a")
        }
        ndkVersion = "27.1.12297006"
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/jni/CMakeLists.txt")
            version = "4.1.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation(libs.androidx.activityCompose)

    // Upgrade BOM to 2024.10.01 to ensure Material3 1.3.0+ (supports ripple() API)
    // This fixes the "Unresolved reference: ripple" build error and the "IndicationNodeFactory" runtime crash.
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.androidx.camera.view)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.extensions)
    implementation("androidx.camera:camera-video:1.3.1")

    implementation(libs.androidx.startup.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.atomicfu)
    implementation(libs.kermit)
    implementation(libs.coil3)
    implementation(libs.coil3.compose)
    implementation("com.composables:icons-lucide-cmp:2.2.1")

    // Plugins dependencies
    // implementation(libs.core) // Removed QR Zxing
    implementation(libs.text.recognition) // MLKit
}