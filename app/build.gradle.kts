plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.kapt")
}

import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("local.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.example.musicnow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.musicnow"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                val keyStoreFile = keystoreProperties.getProperty("KEYSTORE_FILE")
                val keyStorePassword = keystoreProperties.getProperty("KEYSTORE_PASSWORD")
                val keyAlias = keystoreProperties.getProperty("KEY_ALIAS")
                val keyPassword = keystoreProperties.getProperty("KEY_PASSWORD")

                if (keyStoreFile != null && keyStorePassword != null && keyAlias != null && keyPassword != null) {
                    storeFile = rootProject.file(keyStoreFile)
                    storePassword = keyStorePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                } else {
                    println("Signing config for 'release' is incomplete in local.properties. Skipping.")
                }
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
            // signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Retrofit для API запросов
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    
    // Coil для загрузки изображений
    implementation("io.coil-kt:coil-compose:2.4.0")
    
    // ExoPlayer для воспроизведения аудио
    implementation("com.google.android.exoplayer:exoplayer-core:2.18.7")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.18.7")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    
    // Material Icons
    implementation("androidx.compose.material:material-icons-core:1.5.4")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation(libs.androidx.room.common.jvm)

    implementation ("androidx.room:room-runtime:2.7.1")
    implementation ("androidx.room:room-ktx:2.7.1")
    kapt("androidx.room:room-compiler:2.7.1")

    implementation("androidx.compose.material:material:1.5.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

}