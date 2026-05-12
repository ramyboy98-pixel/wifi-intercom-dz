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

        versionCode = 6

        versionName = "6.0"
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

        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }

    packaging {

        resources {

            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(
        "androidx.core:core:1.13.1"
    )

    implementation(
        "androidx.appcompat:appcompat:1.7.0"
    )

    implementation(
        "com.google.android.material:material:1.12.0"
    )
}
