plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ptylr.librearm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ptylr.librearm"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("sharedDebug") {
            // Convenience config so forks/PR builds can sign locally using the in-repo debug keystore.
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("sharedDebug")
        }
        debug {
            signingConfig = signingConfigs.getByName("sharedDebug")
        }
    }

    lint {
        // Our code is lint-clean: BLE/notification calls that lint flags for
        // MissingPermission are @SuppressLint-annotated where the runtime guard
        // lives in a helper lint can't follow. Fail the build on any lint error.
        abortOnError = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.google.material)
    implementation(libs.androidx.health.connect.client)
}

// Bundle the canonical privacy policy so the in-app viewer can show it offline
// (the app holds no INTERNET permission). PRIVACY.md at the repo root stays the
// single source; this copies it into assets at build time.
val copyPrivacyPolicy by tasks.registering(Copy::class) {
    from(rootProject.file("PRIVACY.md"))
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "privacy_policy.md" }
}
tasks.named("preBuild") { dependsOn(copyPrivacyPolicy) }
