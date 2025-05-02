plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.bilawoga"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.bilawoga"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // Update to Java 17
        targetCompatibility = JavaVersion.VERSION_17 // Update to Java 17
    }

    // Java Toolchain configuration
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17)) // Specify Java version
        }
    }
}
dependencies {
    // AndroidX Test Core
    androidTestImplementation(libs.core)

    // JUnit for Android Instrumentation Tests
    androidTestImplementation(libs.junit.v121)

    // Espresso for UI Testing
    androidTestImplementation(libs.espresso.core.v361)
}


dependencies {
    implementation(libs.monitor)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.shake.detector)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
}
