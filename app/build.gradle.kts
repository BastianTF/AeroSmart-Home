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

        buildConfigField("String", "LINK_KEY_HEX", "\"${localProps.getProperty("aerosmart.linkKey", "00000000000000000000000000000000")}\"")
        buildConfigField("String", "DEVICE_HOST", "\"${localProps.getProperty("aerosmart.host", "192.168.4.1")}\"")
        buildConfigField("int", "DEVICE_PORT", localProps.getProperty("aerosmart.port", "3333"))
        // Inicio con Google: "ID de cliente web" de OAuth (Firebase > Authentication > Google). Ver docs/GUIA_EQUIPO.md
        // Es público (no es secreto): queda por defecto para que compile en cualquier PC.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProps.getProperty("aerosmart.googleWebClientId", "237695332072-bdk4rtmt0vdie0peifh4cfglbicd03dp.apps.googleusercontent.com")}\"")
    }

    // Llave de depuración compartida del equipo (app/debug.keystore): así todos los PCs generan
    // el mismo SHA-1 y el inicio con Google funciona sin registrar cada PC en Firebase.
    // Solo sirve para pruebas; NO usar para publicar en Play Store.
    val teamDebugKeystore = rootProject.file("app/debug.keystore")
    if (teamDebugKeystore.exists()) {
        signingConfigs {
            getByName("debug") {
                storeFile = teamDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1") // await() de Firebase
    // Inicio de sesión con Google (Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
