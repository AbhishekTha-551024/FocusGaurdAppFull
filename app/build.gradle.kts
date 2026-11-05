import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// ─────────────────────────────────────────────────────────────────────────────
// Emergency Support Passcode master secret (ECC rules/kotlin/security.md:
// "Never hardcode API keys, tokens, or credentials in source code").
//
// Resolution order (first non-blank wins):
//   1. Gradle property  -Psupport.master.secret=<value>   (CI/CD)
//   2. Env variable     FOCUSGUARD_SUPPORT_SECRET          (CI/CD)
//   3. local.properties support.master.secret=<value>      (local dev, git-ignored)
//
// The build FAILS FAST when the secret is unavailable, exactly as the security
// rule requires ("Validate that required secrets are present at startup").
// ─────────────────────────────────────────────────────────────────────────────
val localSupportSecretProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) localFile.inputStream().use { load(it) }
}

val supportMasterSecret: String = sequenceOf(
    providers.gradleProperty("support.master.secret").orNull,
    System.getenv("FOCUSGUARD_SUPPORT_SECRET"),
    localSupportSecretProperties.getProperty("support.master.secret")
).firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()

require(supportMasterSecret.isNotEmpty()) {
    "FocusGuard support-code master secret is missing. Provide it with one of:\n" +
        "  1. local.properties        -> support.master.secret=<value>   (git-ignored, local dev)\n" +
        "  2. environment variable    -> FOCUSGUARD_SUPPORT_SECRET=<value>\n" +
        "  3. gradle property         -> -Psupport.master.secret=<value>"
}
require(!supportMasterSecret.contains('"') && !supportMasterSecret.contains('\\')) {
    "support.master.secret must not contain a double quote or a backslash: it is embedded into a " +
        "BuildConfig String literal and would break the generated source."
}

android {
    namespace = "com.focusguard.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.focusguard.app"
        minSdk = 26              // API 26 (Android 8): covers >95% of active devices
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SUPPORT_MASTER_SECRET", "\"$supportMasterSecret\"")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "focusguarddebug"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true        // SupportCodeEngine reads BuildConfig.SUPPORT_MASTER_SECRET
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)

    // WorkManager (Standard runtime)
    implementation(libs.androidx.work)

    // Compose — version controlled by BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.coroutines.android)

    // Google Sign-In & Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.identity.googleid)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
