import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
}


val localProps = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val releaseStorePassword = System.getenv("HYPERMUSIC_KEYSTORE_PASSWORD")
    ?: System.getenv("STORE_PASSWORD")
    ?: localProps.getProperty("RELEASE_KEYSTORE_PASSWORD")
    ?: ""
val releaseKeyPassword = System.getenv("HYPERMUSIC_KEY_PASSWORD")
    ?: System.getenv("KEY_PASSWORD")
    ?: localProps.getProperty("RELEASE_KEY_PASSWORD")
    ?: ""
val releaseKeyAlias = System.getenv("HYPERMUSIC_KEY_ALIAS")
    ?: System.getenv("KEY_ALIAS")
    ?: localProps.getProperty("RELEASE_KEY_ALIAS")
    ?: "hypermusic"


android {
    namespace = "com.hypermusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hypermusic"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PROJECT_REPOSITORY_URL", "\"https://github.com/SachinYedav/hypermusic\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    sourceSets.named("main") {
        assets.srcDir(rootProject.file("legal"))
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("hypermusic-release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val keystoreFile = file("hypermusic-release.keystore")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        ignoreTestSources = true
        textReport = true
        htmlReport = true
    }


    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val verifyReleaseSigning by tasks.registering {
    doLast {
        check(file("hypermusic-release.keystore").isFile && releaseStorePassword.isNotBlank() &&
            releaseKeyPassword.isNotBlank() && releaseKeyAlias.isNotBlank()) {
            "Release packaging requires the native app release keystore and signing credentials. Use assembleDebug for QA."
        }
    }
}
tasks.matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // AndroidX Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose BOM & UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation Compose & Kotlin Serialization
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Dependency Injection (Koin)
    implementation(libs.koin.compose)

    // Storage: DataStore & Room Database
    implementation(libs.datastore)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Media3 Audio Engine
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    // Networking & Extractor Engine
    implementation(libs.okhttp)

    implementation(libs.newpipe.extractor)

    // Image Loading & Palette
    implementation(libs.coil.compose)
    implementation(libs.palette)

    // Background WorkManager
    implementation(libs.workmanager)

    // UI Drag & Drop Reordering
    implementation(libs.reorderable)

    // About Libraries
    implementation(libs.aboutlibraries.compose)
}
