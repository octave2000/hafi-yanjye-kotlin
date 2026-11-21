plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun apiUrl(name: String): String =
    (providers.gradleProperty(name).orNull ?: "").trim().trimEnd('/')

// The Google Maps key lives in local.properties (git-ignored); empty means the map shows its fallback panel.
val mapsApiKey: String = rootProject.file("local.properties").takeIf { it.exists() }
    ?.readLines()
    ?.firstOrNull { it.startsWith("MAPS_API_KEY=") }
    ?.substringAfter("=")?.trim()
    ?: ""

android {
    namespace = "com.hafiyanjye.app"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.hafiyanjye.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        for (name in listOf("FIREBASE_APP_ID", "FIREBASE_API_KEY", "FIREBASE_PROJECT_ID", "FIREBASE_SENDER_ID")) {
            val value = providers.gradleProperty(name).orNull.orEmpty()
            buildConfigField("String", name, "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        // Where the web app lives: public CV links point there (the engine does not serve those pages).
        buildConfigField("String", "WEB_BASE_URL", "\"${apiUrl("webUrl")}\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "API_BASE_URL", "\"${apiUrl("debugApiUrl")}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"${apiUrl("releaseApiUrl")}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
