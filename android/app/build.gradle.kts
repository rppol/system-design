plugins {
    id("com.android.application") version "8.7.2"
    id("org.jetbrains.kotlin.android") version "2.1.0"
}

android {
    namespace = "io.github.rppol.sddaily"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.rppol.sddaily"
        minSdk = 29
        targetSdk = 35
        // CI derives these (versionCode = commit count, versionName = date-sha);
        // locals fall back to 1/"dev".
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "dev"
    }

    // Release signing is driven entirely by environment variables so that CI can
    // supply a keystore while a local `./gradlew assembleDebug` needs nothing.
    // The config is only wired onto the release build type when KEYSTORE_PATH is
    // present; otherwise assembleRelease would demand a keystore that isn't there.
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
