import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.thelightphone.sdk.emulator"
    compileSdk = rootProject.ext["compileSdk"] as Int

    val platformKeystore = file("keys/platform.jks")
    val hasPlatformKey = platformKeystore.exists()

    signingConfigs {
        create("lightsdkDev") {
            storeFile = file("../keys/lightsdk-dev.jks")
            storePassword = "android"
            keyAlias = "lightsdk-dev"
            keyPassword = "android"
            enableV3Signing = true
            enableV4Signing = true
        }
        if (hasPlatformKey) {
            create("platform") {
                storeFile = platformKeystore
                storePassword = "android"
                keyAlias = "platform"
                keyPassword = "android"
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    if (!hasPlatformKey) {
        logger.warn("WARNING: Platform keystore not found at sdk/emulator/keys/platform.jks")
        logger.warn("The emulator app will not run as uid 1000 without platform signing.")
        logger.warn("See README.md for instructions on generating the keystore.")
    }

    val localProps = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }

    defaultConfig {
        applicationId = "com.thelightphone.sdk.emulator"
        minSdk = rootProject.ext["minSdk"] as Int
        targetSdk = rootProject.ext["targetSdk"] as Int
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "PUSH_DOMAIN", "\"${localProps.getProperty("pushDomain", "")}\"")
        buildConfigField("String", "MOLLYSOCKET_URI", "\"${localProps.getProperty("mollysocketUri", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            signingConfig = if (hasPlatformKey) signingConfigs.getByName("platform") else signingConfigs.getByName("lightsdkDev")
        }
        release {
            signingConfig = if (hasPlatformKey) signingConfigs.getByName("platform") else signingConfigs.getByName("lightsdkDev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
        targetCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
    }

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.*"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(rootProject.ext["jvmTarget"] as String))
    }
}

dependencies {
    implementation(project(":sdk:server"))
    implementation(project(":sdk:ui"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.media3.session)
}
