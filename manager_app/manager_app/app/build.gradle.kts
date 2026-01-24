plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.danmuapi.manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.danmuapi.manager"
        minSdk = 21
        targetSdk = 34

        // Patched by workflow to match module version
        versionCode = 100
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../signing/danmu_manager.jks")
            storePassword = "danmu_manager"
            keyAlias = "danmu_manager"
            keyPassword = "danmu_manager"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // AboutScreen references BuildConfig.VERSION_NAME / VERSION_CODE
        // (AGP 8+ allows disabling this; keep it explicitly enabled.)
        buildConfig = true
    }
    composeOptions {
        // Kotlin 1.9.22 compatible
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM keeps Compose artifacts in sync
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))

    // Material Components for XML theme resources (Theme.Material3.*)
    // (Compose Material3 does not provide these XML styles.)
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // collectAsStateWithLifecycle()
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // WebView support (dark mode, feature checks)
    implementation("androidx.webkit:webkit:1.14.0")

    // DataStore for app settings (log auto-clean interval, GitHub token)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager for scheduled log cleanup (event-driven, no polling loops)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Networking for update check
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
