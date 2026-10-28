import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val devCredentialNames = listOf("DEV_EMAIL", "DEV_PASSWORD")
val devTargetUrls = mapOf(
    "emulator" to "http://10.0.2.2:3000",
    // Requiere `adb reverse tcp:3000 tcp:3000` con el dispositivo conectado por USB.
    "device" to "http://localhost:3000",
)
val devTarget = localProperties.getProperty("DEV_TARGET", "emulator").trim()
val devBackendUrl = localProperties.getProperty("BACKEND_BASE_URL")?.takeIf { it.isNotBlank() }
    ?: devTargetUrls[devTarget].orEmpty()
val devBuildConfig = mapOf(
    "BACKEND_BASE_URL" to devBackendUrl,
    "DEV_EMAIL" to localProperties.getProperty("DEV_EMAIL", ""),
    "DEV_PASSWORD" to localProperties.getProperty("DEV_PASSWORD", ""),
)

fun buildConfigString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.rocioplata.familyfinaiapp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.rocioplata.familyfinaiapp"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            devBuildConfig.forEach { (name, value) ->
                buildConfigField("String", name, buildConfigString(value))
            }
        }
        release {
            devBuildConfig.keys.forEach { name ->
                buildConfigField("String", name, "\"\"")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val missingDevProperties = devCredentialNames.filter { localProperties.getProperty(it).isNullOrBlank() }
val invalidDevTarget = devTarget !in devTargetUrls && localProperties.getProperty("BACKEND_BASE_URL").isNullOrBlank()

val checkDevProperties by tasks.registering {
    val missing = missingDevProperties
    val invalidTarget = invalidDevTarget
    val target = devTarget
    doLast {
        if (invalidTarget) {
            throw GradleException("DEV_TARGET=\"$target\" no es válido en local.properties; usa emulator o device.")
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Faltan propiedades en local.properties (no versionado): ${missing.joinToString()}. " +
                    "Ejemplo: DEV_TARGET=emulator, DEV_EMAIL=..., DEV_PASSWORD=...",
            )
        }
    }
}

tasks.matching { it.name == "preDebugBuild" }.configureEach {
    dependsOn(checkDevProperties)
}
