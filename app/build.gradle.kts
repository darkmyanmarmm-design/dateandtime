plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mhtime.tvauto"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mhtime.tvauto"
        minSdk = 24          // Direct Boot (device-protected storage) needs API 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Fixed debug key, committed to the repo, so every CI build has the SAME signature.
        // Without this, each GitHub runner creates a new debug keystore and you must uninstall
        // the app between builds, which wipes the stored ADB key and triggers a new prompt.
        getByName("debug") {
            val ks = file("debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            // Signed with the same fixed key so the release APK also installs over the debug one.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
