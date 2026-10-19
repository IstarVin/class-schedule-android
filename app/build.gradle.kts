import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.istarvin.classschedule2"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.istarvin.classschedule2"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
            storeFile = (System.getenv("ANDROID_KEYSTORE_FILE") ?: keystoreProperties.getProperty("storeFile"))
                ?.let { rootProject.file(it) }
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: keystoreProperties.getProperty("storePassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets.getByName("test").resources.directories.add("src/main/assets")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    testImplementation(libs.junit)
    // Android's org.json implementation is stubbed in local JVM tests.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
