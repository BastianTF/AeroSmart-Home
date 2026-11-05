import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// Datos del enlace con la placa: se leen de local.properties (no se sube al repositorio).
//   aerosmart.linkKey=<32 o 64 caracteres hexadecimales, la misma clave de arduino/AeroSmartHome/secrets.h>
//   aerosmart.host=192.168.4.1   (opcional)
//   aerosmart.port=3333          (opcional)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.ventiladorambiental"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.ventiladorambiental"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "LINK_KEY_HEX", "\"${localProps.getProperty("aerosmart.linkKey", "")}\"")
        buildConfigField("String", "DEVICE_HOST", "\"${localProps.getProperty("aerosmart.host", "192.168.4.1")}\"")
        buildConfigField("int", "DEVICE_PORT", localProps.getProperty("aerosmart.port", "3333"))
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Firebase BoM y Servicios IoT (Autenticación y Realtime Database)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
