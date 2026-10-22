plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.doublesail.scheduleboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.doublesail.scheduleboard"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val distributionKeystore = providers.environmentVariable("SCHEDULEBOARD_KEYSTORE").orNull
    signingConfigs {
        if (distributionKeystore != null) create("distribution") {
            storeFile = file(distributionKeystore)
            storePassword = providers.environmentVariable("SCHEDULEBOARD_STORE_PASSWORD").getOrElse("android")
            keyAlias = providers.environmentVariable("SCHEDULEBOARD_KEY_ALIAS").getOrElse("androiddebugkey")
            keyPassword = providers.environmentVariable("SCHEDULEBOARD_KEY_PASSWORD").getOrElse("android")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (distributionKeystore != null) signingConfig = signingConfigs.getByName("distribution")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isReturnDefaultValues = false
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "1536m"
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.systemProperty("board.render.output", layout.buildDirectory.dir("reports/board-render").get().asFile.absolutePath)
        }
    }
    if (providers.gradleProperty("renderBoard").isPresent) {
        sourceSets.getByName("test").java.srcDir("src/renderTest/java")
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

tasks.matching { it.name == "assembleRelease" }.configureEach {
    doFirst {
        require(providers.environmentVariable("SCHEDULEBOARD_KEYSTORE").isPresent) {
            "Set SCHEDULEBOARD_KEYSTORE to the fixed distribution keystore; unsigned releases must not be distributed."
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.jsoup)
    implementation(libs.androidx.work.runtime)
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    if (providers.gradleProperty("renderBoard").isPresent) {
        testImplementation("org.robolectric:robolectric:4.16.1")
        testImplementation(libs.compose.ui.test.junit4)
        testImplementation(libs.androidx.junit)
    }
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
