plugins {
    id("com.android.application")
}

android {

    namespace = "com.idaradz.wifiintercom"

    compileSdk = 36

    defaultConfig {

        applicationId = "com.idaradz.wifiintercom"

        minSdk = 23

        targetSdk = 36

        versionCode = 2

        versionName = "2.0"
    }

    buildTypes {

        release {

            isMinifyEnabled = false
        }
    }

    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_17

        targetCompatibility = JavaVersion.VERSION_17
    }
}
