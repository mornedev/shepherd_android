plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

// Read API base URL from Gradle property or environment variable with a sensible default
val apiBaseUrl: String = (project.findProperty("API_BASE_URL") as String?)
    ?: System.getenv("API_BASE_URL")
    ?: "https://legacyshepherd-api-601845597651.australia-southeast1.run.app"

android {
    namespace = "com.mamlambofossils.shepherd"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mamlambofossils.shepherd"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Make API base URL available in BuildConfig
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    // Coroutines + lifecycle scope for launching suspend calls from Activity
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    // Supabase + Ktor (explicit coordinates to unblock immediately)
    implementation(platform("io.github.jan-tennert.supabase:bom:2.5.0"))
    implementation("io.github.jan-tennert.supabase:supabase-kt") // provides createSupabaseClient
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-android:2.3.11")
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("org.slf4j:slf4j-android:1.7.36")
    // CameraX for in-app camera
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}