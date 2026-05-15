plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

import java.util.Properties

val keystoreProperties = Properties()
val keystoreFile = rootProject.file("keystore.properties")
if (keystoreFile.exists()) {
    keystoreFile.inputStream().use { keystoreProperties.load(it) }
}

// Load local properties to fetch MAPS_API_KEY without committing it
val localProperties = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProperties.load(it) }
}
val mapsApiKey: String = (localProperties["MAPS_API_KEY"] as String?) ?: System.getenv("MAPS_API_KEY") ?: ""

android {
    namespace = "com.bilawoga.safety"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bilawoga.safety"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"

        buildConfigField("String", "SIGNATURE_SHA256", "\"\"")

        // Expose Maps key to manifest
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystoreFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystoreFile.exists()) signingConfigs.getByName("release") else null
            // Release certificate SHA-256 for integrity checks
            buildConfigField("String", "SIGNATURE_SHA256", "\"0F:8D:88:8D:7C:2E:32:AC:8D:53:B2:0C:2F:DC:D1:04:18:42:D1:00:A9:86:D7:1B:0F:8D:F2:8F:00:99:FE:F6\"")
            // Generate native debug symbols for crash analysis
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            buildConfigField("String", "SIGNATURE_SHA256", "\"\"")
        }
    }
    
    lint {
        abortOnError = false  // Don't abort on lint errors for Play Store submission
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    buildFeatures {
        buildConfig = true
    }
    
    // Enable bundle generation for Play Store
    bundle {
        language {
            enableSplit = false  // Include all languages in base bundle
        }
        density {
            enableSplit = false  // Include all densities in base bundle
        }
        abi {
            enableSplit = true  // Split by ABI for smaller downloads
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
    implementation(libs.monitor)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.shake.detector)
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-base:18.2.0")
    implementation("com.google.firebase:firebase-crashlytics:18.6.2")
    implementation("com.google.firebase:firebase-analytics:21.5.1")
    implementation("com.google.firebase:firebase-perf:20.5.1")
    implementation("com.google.firebase:firebase-auth:22.3.1")
    implementation("com.google.firebase:firebase-firestore:25.1.1")
    implementation("com.google.firebase:firebase-installations:17.2.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    // Biometric authentication library
    implementation("androidx.biometric:biometric:1.1.0")
    // Use local JAR for TarsosDSP
    implementation(files("libs/TarsosDSP-2.4.jar"))
    // CSV parsing library for training data
    implementation("com.opencsv:opencsv:5.7.1")
}

