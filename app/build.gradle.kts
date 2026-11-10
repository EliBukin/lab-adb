import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "bukin.labadb"
    compileSdk = 36

    defaultConfig {
        applicationId = "bukin.labadb"
        // Android 11 is the first version with Wireless debugging.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// No runtime libraries on purpose: the platform APIs are enough from Android 11 on, and AndroidX
// core would add its own permission and components to the merged manifest.
dependencies {
    testImplementation(libs.junit)
}
