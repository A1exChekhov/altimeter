plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val androidSigningStoreFile = System.getenv("ANDROID_SIGNING_STORE_FILE")
val androidSigningStorePassword = System.getenv("ANDROID_SIGNING_STORE_PASSWORD")
val androidSigningKeyAlias = System.getenv("ANDROID_SIGNING_KEY_ALIAS")
val androidSigningKeyPassword = System.getenv("ANDROID_SIGNING_KEY_PASSWORD")
val googleMapsApiKey = System.getenv("GOOGLE_MAPS_ANDROID_API_KEY")
    ?: (project.findProperty("GOOGLE_MAPS_ANDROID_API_KEY") as? String).orEmpty()
val huaweiAppId = System.getenv("HUAWEI_APP_ID")
    ?: (project.findProperty("HUAWEI_APP_ID") as? String).orEmpty()

android {
    namespace = "com.chelmodeev.altimeter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chelmodeev.altimeter"
        minSdk = 26
        targetSdk = 35
        versionCode = 28
        versionName = "1.6.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val watchPkg = (project.findProperty("WATCH_APP_PACKAGE") as? String).orEmpty()
        val watchFp = (project.findProperty("WATCH_APP_FINGERPRINT") as? String).orEmpty()
        buildConfigField("String", "WATCH_APP_PACKAGE", "\"$watchPkg\"")
        buildConfigField("String", "WATCH_APP_FINGERPRINT", "\"$watchFp\"")
        buildConfigField("String", "HUAWEI_APP_ID", "\"$huaweiAppId\"")
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$googleMapsApiKey\"")
        manifestPlaceholders["huaweiAppId"] = huaweiAppId.ifBlank { "0" }
        manifestPlaceholders["googleMapsApiKey"] = googleMapsApiKey.ifBlank { "UNCONFIGURED" }
    }

    signingConfigs {
        if (!androidSigningStoreFile.isNullOrBlank()) {
            create("stableRelease") {
                storeFile = file(androidSigningStoreFile)
                storePassword = androidSigningStorePassword
                keyAlias = androidSigningKeyAlias
                keyPassword = androidSigningKeyPassword
            }
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
            signingConfig = if (!androidSigningStoreFile.isNullOrBlank()) {
                signingConfigs.getByName("stableRelease")
            } else {
                signingConfigs.getByName("debug")
            }
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
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.osmdroid)
    implementation(libs.maplibre)
    implementation(libs.play.services.maps)
    implementation(libs.wearengine)
    implementation(libs.huawei.health)
    implementation(libs.androidx.health.connect)
    // Wear Engine тянет старый fragment; ActivityResult API требует >= 1.3.0
    implementation(libs.androidx.fragment.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    testImplementation("junit:junit:4.13.2")
}
