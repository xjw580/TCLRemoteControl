plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "club.xiaojiawei.tclremotecontrol"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "club.xiaojiawei.tclremotecontrol"
        minSdk = 28
        targetSdk = 37
        versionCode = 13
        versionName = "0.4.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
