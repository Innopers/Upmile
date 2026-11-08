import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 릴리스 서명 키는 local.properties(커밋 금지)에서 읽는다.
val signingProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").map { "upmile.$it" }
val hasReleaseKey = signingKeys.all { !signingProps.getProperty(it).isNullOrBlank() }

android {
    namespace = "app.upmile"
    compileSdk = 36
    useLibrary("android.car")

    defaultConfig {
        applicationId = "app.upmile"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(signingProps.getProperty("upmile.storeFile"))
                storePassword = signingProps.getProperty("upmile.storePassword")
                keyAlias = signingProps.getProperty("upmile.keyAlias")
                keyPassword = signingProps.getProperty("upmile.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // The TTS AAR ships no consumer rules and uses gson.
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("upmile.* 서명 키가 local.properties에 없어 릴리스 빌드를 debug 키로 서명합니다.")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].assets.srcDir("../assets")
    sourceSets["test"].resources.srcDir("../assets")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.fusedlocation)
    implementation(libs.pleos.tts)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.junit)
}
