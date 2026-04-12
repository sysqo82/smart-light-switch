import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.example.smartlightswitch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smartlightswitch"
        minSdk = 23
        targetSdk = 35
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        versionCode = 1
        versionName = "1.0"

        // ─── Tuya / ThingClips credentials ───────────────────────────────────────
        // 1. Register at https://iot.tuya.com
        // 2. Create a "Smart Home" app project and copy the AppKey / AppSecret below.
        // 3. Add your debug keystore SHA-256 fingerprint to that project:
        //    keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore
        //             -alias androiddebugkey -storepass android -keypass android
        // ─────────────────────────────────────────────────────────────────────────
        buildConfigField("String", "TUYA_APP_KEY",    "\"${localProperties["TUYA_APP_KEY"] ?: ""}\"")
        buildConfigField("String", "TUYA_APP_SECRET", "\"${localProperties["TUYA_APP_SECRET"] ?: ""}\"")
    }

    packaging {
        jniLibs {
            pickFirsts += "lib/*/libc++_shared.so"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig  = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

configurations.all {
    exclude(group = "com.thingclips.smart", module = "thingsmart-modularCampAnno")
}

dependencies {
    // Security AAR — download from iot.tuya.com > App SDK > Get SDK, place in app/libs/
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // Tuya / ThingClips Smart Home SDK (latest stable)
    implementation("com.thingclips.smart:thingsmart:6.11.6")
    implementation("com.alibaba:fastjson:1.1.67.android")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:3.14.9")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
}
