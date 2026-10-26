plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Filled in automatically by the GitHub build
val ciVersion = (System.getenv("VERSION_CODE") ?: "1").toInt()
val repo = System.getenv("GITHUB_REPOSITORY") ?: ""

android {
    namespace = "app.medicinely"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.medicinely"
        minSdk = 27
        targetSdk = 34
        versionCode = ciVersion
        versionName = "1.$ciVersion"
        buildConfigField("String", "REPO", "\"$repo\"")
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "medicinely"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
