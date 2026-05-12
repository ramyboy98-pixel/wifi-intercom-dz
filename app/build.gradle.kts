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

        versionCode = 3

        versionName = "3.0"
    }

    buildTypes {

        debug {

            isDebuggable = true
        }

        release {

            isMinifyEnabled = false
        }
    }

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }
}
