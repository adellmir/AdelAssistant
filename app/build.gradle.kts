plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.adel.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.adel.assistant"
        minSdk = 26
        targetSdk = 34
        // base 2000 + شماره بیلد CI → همیشه از نسخهٔ قبلی بزرگ‌تر و قابل آپدیت
        val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: System.getenv("VERSION_CODE") ?: "0").toIntOrNull() ?: 0
        val code = 2000 + runNumber
        versionCode = code
        versionName = "1.0.$code"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
}
