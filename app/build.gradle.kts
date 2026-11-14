plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

val signingStore = System.getenv("MATABAAT_KEYSTORE_PATH")
val signingStorePassword = System.getenv("MATABAAT_KEYSTORE_PASSWORD")
val signingAlias = System.getenv("MATABAAT_KEY_ALIAS")
val signingKeyPassword = System.getenv("MATABAAT_KEY_PASSWORD")

android {
    namespace = "com.roadalert.egypt"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.roadalert.egypt"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.08"
    }
    signingConfigs {
        if (!signingStore.isNullOrBlank()) {
            create("stableRelease") {
                storeFile = file(signingStore)
                storePassword = signingStorePassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!signingStore.isNullOrBlank()) signingConfig = signingConfigs.getByName("stableRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
