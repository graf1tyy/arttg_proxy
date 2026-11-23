plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.artunnel.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.artunnel.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    buildTypes {
        getByName("release") {
            // Preview signing only. Configure a persistent private key before store distribution.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    sourceSets.getByName("main").java.srcDir("../core/src/commonMain/kotlin")
}

dependencies {
    implementation("net.java.dev.jna:jna:5.19.1@aar")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
}
