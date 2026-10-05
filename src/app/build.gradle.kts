plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val semanticVersion = rootProject.projectDir.parentFile.resolve("VERSION").readText().trim()
val versionParts = semanticVersion.split('.').map(String::toInt)
require(versionParts.size == 3) { "VERSION must be MAJOR.MINOR.PATCH" }

val signingKeyPath = System.getenv("DICETHROWER_KEYSTORE_PATH")
val signingKeyPassword = System.getenv("DICETHROWER_KEYSTORE_PASSWORD")
val requireStableSigning = providers.gradleProperty("requireStableSigning").orNull == "true"
val googleWebClientId = System.getenv("DICETHROWER_GOOGLE_WEB_CLIENT_ID").orEmpty()

require(signingKeyPath.isNullOrBlank() == signingKeyPassword.isNullOrBlank()) {
    "DICETHROWER_KEYSTORE_PATH and DICETHROWER_KEYSTORE_PASSWORD must be configured together"
}
if (requireStableSigning) {
    require(!signingKeyPath.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()) {
        "Stable signing is required: configure DICETHROWER_KEYSTORE_PATH and DICETHROWER_KEYSTORE_PASSWORD"
    }
    require(file(signingKeyPath).isFile) {
        "Stable signing keystore does not exist at DICETHROWER_KEYSTORE_PATH"
    }
}

android {
    namespace = "com.keyserdsoze.dicethrower"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.keyserdsoze.dicethrower"
        minSdk = 26
        targetSdk = 37
        versionCode = versionParts[0] * 10_000 + versionParts[1] * 100 + versionParts[2]
        versionName = semanticVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "google_web_client_id", googleWebClientId)
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (!signingKeyPath.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()) {
            create("stableRelease") {
                storeFile = file(signingKeyPath)
                storePassword = signingKeyPassword
                keyAlias = "dicethrower"
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (signingConfigs.names.contains("stableRelease")) {
                signingConfig = signingConfigs.getByName("stableRelease")
            }
        }
        release {
            if (signingConfigs.names.contains("stableRelease")) {
                signingConfig = signingConfigs.getByName("stableRelease")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    implementation("com.google.android.gms:play-services-auth:21.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
