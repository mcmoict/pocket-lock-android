plugins {
    id("com.android.application")
}

android {
    namespace = "com.innoshiftconsult.pocketlock"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.innoshiftconsult.pocketlock"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "0.1.17"
    }
}
