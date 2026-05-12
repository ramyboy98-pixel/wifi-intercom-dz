plugins {
    id("com.android.application")
}

android {
    namespace = "com.idaradz.wifiintercom"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.idaradz.wifiintercom"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "7.0"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }

        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
