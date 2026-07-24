plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.adsmodule.admob"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    api(project(":ads-sdk-core"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // Public handles expose NativeAd / AdView / InterstitialAd — must be api.
    api(libs.play.services.ads)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
