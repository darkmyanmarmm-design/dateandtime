import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// A real keystore starts with FE ED FE ED (JKS) or 0x30 (PKCS12). Anything else is a damaged file.
fun looksLikeKeystore(f: File): Boolean {
    if (!f.isFile || f.length() < 100) return false
    val head = ByteArray(4)
    f.inputStream().use { it.read(head) }
    val jks = head[0] == 0xFE.toByte() && head[1] == 0xED.toByte() &&
        head[2] == 0xFE.toByte() && head[3] == 0xED.toByte()
    val p12 = head[0] == 0x30.toByte()
    return jks || p12
}

val fixedKeystore = file("debug.keystore")
val useFixedKeystore = looksLikeKeystore(fixedKeystore)
if (fixedKeystore.exists() && !useFixedKeystore) {
    logger.warn("WARNING: app/debug.keystore is not a valid keystore; falling back to the default debug key.")
}

android {
    namespace = "com.mhtime.tvauto"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mhtime.tvauto"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        getByName("debug") {
            if (useFixedKeystore) {
                storeFile = fixedKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
