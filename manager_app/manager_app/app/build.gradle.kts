plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.danmuapi.manager"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.danmuapi.manager"
    minSdk = 23
    targetSdk = 34

    // Patched by GitHub Actions to match module version
    versionCode = 100
    versionName = "1.0.0"
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
    getByName("release") {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
    }
    getByName("debug") {
      isMinifyEnabled = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.12.0")
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("com.google.android.material:material:1.11.0")
}
