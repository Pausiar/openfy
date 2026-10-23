import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun localProperty(name: String): String {
    val properties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(properties::load)
    }
    return properties.getProperty(name) ?: System.getenv(name) ?: ""
}

/**
 * Reads a value from the shared `version.properties` file, the single source of truth
 * for the app version (also consumed by CI and by the web download manifest).
 */
fun versionProperty(name: String): String {
    val properties = Properties()
    val versionFile = rootProject.file("version.properties")
    if (!versionFile.exists()) {
        throw GradleException("Falta version.properties en la raíz del proyecto.")
    }
    versionFile.inputStream().use(properties::load)
    return properties.getProperty(name)?.trim().orEmpty()
}

val appVersionName = versionProperty("versionName").ifBlank {
    throw GradleException("version.properties no define versionName.")
}
val appVersionCode = versionProperty("versionCode").toIntOrNull() ?: run {
    throw GradleException("version.properties no define un versionCode numérico válido.")
}

val releaseSigningKeys = listOf(
    "OPENFY_KEYSTORE_PATH",
    "OPENFY_KEYSTORE_PASSWORD",
    "OPENFY_KEY_ALIAS",
    "OPENFY_KEY_PASSWORD"
)

fun hasReleaseSigning(): Boolean = releaseSigningKeys.all { localProperty(it).isNotBlank() }

android {
    namespace = "com.pausiar.openfy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pausiar.openfy.player"
        minSdk = 28
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "YOUTUBE_API_KEY", "\"${localProperty("OPENFY_YOUTUBE_API_KEY")}\"")
        buildConfigField("String", "SPOTIFY_METADATA_BASE_URL", "\"${localProperty("OPENFY_SPOTIFY_METADATA_BASE_URL")}\"")
    }

    signingConfigs {
        create("release") {
            val keystorePath = localProperty("OPENFY_KEYSTORE_PATH")
            if (keystorePath.isNotBlank()) {
                storeFile = file(keystorePath)
                storePassword = localProperty("OPENFY_KEYSTORE_PASSWORD")
                keyAlias = localProperty("OPENFY_KEY_ALIAS")
                keyPassword = localProperty("OPENFY_KEY_PASSWORD")
            }
            // Firma con los esquemas que Android 9+ (minSdk 28) soporta:
            //   v2 -> verificación completa del APK
            //   v3 -> añade soporte de rotación de claves y metadatos de firma
            // v1 (JAR signing) no aporta nada con minSdk 28 y penaliza el tamaño.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // La APK release SIEMPRE usa la clave de release. Nunca la clave debug:
            // una APK firmada con la clave debug no puede actualizarse en el dispositivo
            // y Android la marca como app de origen desconocido.
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * Falla la build de release si no hay credenciales de firma release.
 *
 * La comprobación se hace al configurar el proyecto, en función de las tareas
 * pedidas en la línea de comandos, para que las builds de debug sigan
 * funcionando sin secretos y el error salte con un mensaje accionable en lugar
 * del error genérico de AGP ("keystore not found").
 */
val releaseSigningError = """
    |Falta la configuración de firma release de Openfy.
    |
    |Define en `local.properties` o como variables de entorno:
    |  OPENFY_KEYSTORE_PATH
    |  OPENFY_KEYSTORE_PASSWORD
    |  OPENFY_KEY_ALIAS
    |  OPENFY_KEY_PASSWORD
    |
    |Genera una clave de release con:
    |  Windows: scripts\create-release-keystore.ps1
    |  Linux/macOS: scripts/create-release-keystore.sh
    |
    |No se publica ninguna APK release firmada con la clave debug: no se podría
    |actualizar en el dispositivo y Android mostraría avisos de aplicación no verificada.
""".trimMargin()

/**
 * `build` y `assemble` incluyen las variantes de release, así que también cuentan
 * como una petición de build release.
 */
val requestedTasks = gradle.startParameter.taskNames.map { it.substringAfterLast(':') }
val wantsReleaseBuild = requestedTasks.any { name ->
    val normalized = name.lowercase()
    normalized == "build" || normalized == "assemble" || normalized.contains("release")
}

if (wantsReleaseBuild && !hasReleaseSigning()) {
    throw GradleException(releaseSigningError)
}

// Red de seguridad: si la tarea de validación de firma existe (la crea AGP), el
// chequeo se repite justo antes de firmar, por si acaso la configuración cambiara
// entre la configuración del proyecto y la ejecución.
tasks.matching { it.name == "validateSigningRelease" }.configureEach {
    doFirst {
        if (!hasReleaseSigning()) {
            throw GradleException(releaseSigningError)
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
