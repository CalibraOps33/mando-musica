plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.mandomusica.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.mandomusica.companion"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Clave de desarrollo fija: así cada APK nuevo se instala encima del anterior
    // sin tener que desinstalar. NO usar para publicar en Google Play.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/dev-debug.keystore")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
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
    // Sin dependencias externas: solo APIs del sistema Android y la librería estándar de Kotlin.
}
