plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.adsdemo"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.adsdemo"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":ads-debug"))
    implementation(project(":ads-core"))
    implementation(project(":ads-sdk-fake"))

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
