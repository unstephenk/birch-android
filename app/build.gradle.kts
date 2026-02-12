plugins {
  id("com.android.application") version "8.2.2"
  id("org.jetbrains.kotlin.android") version "1.9.22"
}

android {
  namespace = "com.birch.podcast"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.birch.podcast"
    minSdk = 30 // Android 11
    targetSdk = 34
    versionCode = 1
    versionName = "0.1"
  }

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.8"
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
  val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
  implementation(composeBom)

  implementation("androidx.core:core-ktx:1.12.0")
  implementation("androidx.activity:activity-compose:1.8.2")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")

  // Networking + feed parse
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("org.jsoup:jsoup:1.17.2")

  // Playback
  implementation("androidx.media3:media3-exoplayer:1.3.1")
  implementation("androidx.media3:media3-common:1.3.1")

  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
