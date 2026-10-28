import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val debugBackendUrl = providers.gradleProperty("AGRO_AI_BACKEND_URL")
    .orElse(providers.environmentVariable("AGRO_AI_BACKEND_URL"))
    .orElse("")
    .get()
val releaseBackendUrl = providers.gradleProperty("AGRO_AI_RELEASE_BACKEND_URL")
    .orElse(providers.environmentVariable("AGRO_AI_RELEASE_BACKEND_URL"))
    .orElse("")
    .get()
require(releaseBackendUrl.isBlank() || releaseBackendUrl.startsWith("https://")) {
    "AGRO_AI_RELEASE_BACKEND_URL must use HTTPS"
}

val releaseStoreFile = providers.environmentVariable("AGRO_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("AGRO_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("AGRO_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("AGRO_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
require(releaseSigningValues.all { it.isNullOrBlank() } || releaseSigningValues.all { !it.isNullOrBlank() }) {
    "Set all AGRO_RELEASE_* variables or none of them"
}
val releaseSigningReady = releaseSigningValues.all { !it.isNullOrBlank() }

fun buildConfigString(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.agroai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.agroai.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningReady) create("release") {
            storeFile = file(requireNotNull(releaseStoreFile))
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "AGRO_AI_BACKEND_URL", buildConfigString(debugBackendUrl))
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            buildConfigField("String", "AGRO_AI_BACKEND_URL", buildConfigString(releaseBackendUrl))
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui:1.9.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.2")
    implementation("androidx.compose.foundation:foundation:1.9.2")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1"))
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    implementation(platform("com.google.firebase:firebase-bom:35.0.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.2")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
