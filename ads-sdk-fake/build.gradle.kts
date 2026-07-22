plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.adsmodule.fake"

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
}

dependencies {
    implementation(project(":ads-sdk-core"))
}
