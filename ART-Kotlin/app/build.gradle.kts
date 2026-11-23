plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.artunnel.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.artunnel.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        getByName("release") {
            // First public preview is signed with the CI debug key so it can be
            // installed directly. A persistent private release key is needed
            // before publishing in-place upgrades to existing installs.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    sourceSets.getByName("main").java.srcDir("../core/src/commonMain/kotlin")
}

kotlin {
    jvmToolchain(17)
}
