plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// F5-04 决策 §1: the in-app license page renders the SAME ledger the release gate validates.
// Single source of truth = docs/third-party-notices.md, copied into assets at build time;
// LedgerAssetSyncTest fails if this wiring stops working.
val noticesAssetsDir = layout.buildDirectory.dir("generated/noticesAssets")
val syncThirdPartyNoticesAsset = tasks.register<Copy>("syncThirdPartyNoticesAsset") {
    group = "verification"
    description = "Copies docs/third-party-notices.md into debug/release assets."
    from(rootProject.layout.projectDirectory.file("docs/third-party-notices.md"))
    into(noticesAssetsDir)
}

android {
    namespace = "com.example.englishlearning"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.englishlearning"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    sourceSets {
        getByName("main").assets.srcDir(noticesAssetsDir)
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        getByName("test").java.srcDir("src/kokoroSpike/kotlin")
        getByName("androidTest").java.srcDir("src/kokoroSpike/kotlin")
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.named("preBuild") { dependsOn(syncThirdPartyNoticesAsset) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    // security-crypto is kept for its registered ledger role, but its bundled legacy
    // tink-android 1.8.0 duplicates classes from com.google.crypto.tink:tink 1.21.0 used
    // by F4-04; exclude the old copy so a single maintained Tink version is on the classpath.
    implementation(libs.androidx.security.crypto) {
        exclude(group = "com.google.crypto.tink", module = "tink-android")
    }
    implementation(libs.onnxruntime.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.tink)
    implementation(libs.bouncy.castle.bcprov)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    // F3-03 photo OCR: ML Kit bundled is the primary local engine, Tesseract4Android the no-GMS fallback
    // (approved in docs/decisions/2026-10-13-f3-03-ocr-provider-licenses.md option A).
    implementation(libs.mlkit.text.recognition)
    implementation(libs.tesseract4android)
    implementation(libs.hilt.android)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.onnxruntime.android)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.named("check") {
    dependsOn(rootProject.tasks.named("verifyThirdPartyNotices"))
    dependsOn("detekt", "ktlintCheck")
}
