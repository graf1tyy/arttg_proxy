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
        versionCode = 2
        versionName = "0.2.0"
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

dependencies {
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
}
