plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.idaradz.wifiintercom"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.idaradz.wifiintercom"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}
