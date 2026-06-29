plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace   = "com.bee.brickbreaker"
    compileSdk  = 36

    defaultConfig {
        applicationId  = "com.bee.brickbreaker"
        minSdk         = 26
        targetSdk      = 36
        versionCode    = 1
        versionName    = "1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
