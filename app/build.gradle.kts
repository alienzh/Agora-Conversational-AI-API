import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.navigation.safe.args)
}

// Load env.properties file for Agora configuration
val envProperties = Properties()
val envPropertiesFile = rootProject.file("env.properties")
if (envPropertiesFile.exists()) {
    envPropertiesFile.inputStream().use { envProperties.load(it) }
}

// Validate required Agora configuration properties
val requiredProperties = listOf(
    "agora.appId",
    "agora.restKey",
    "agora.restSecret",
    "agora.pipelineId"
)

val missingProperties = mutableListOf<String>()
requiredProperties.forEach { key ->
    val value = envProperties.getProperty(key)
    if (value.isNullOrEmpty()) {
        missingProperties.add(key)
    }
}

if (missingProperties.isNotEmpty()) {
    val errorMessage = buildString {
        append("Please configure the following required properties in env.properties:\n")
        missingProperties.forEach { prop ->
            append("  - $prop\n")
        }
        append("\nPlease refer to env.properties for configuration reference.")
    }
    throw GradleException(errorMessage)
}

android {
    namespace = "io.agora.convoai.example"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    defaultConfig {
        applicationId = "io.agora.convoai.example.demo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load Agora configuration from env.properties
        buildConfigField("String", "AGORA_APP_ID", "\"${envProperties.getProperty("agora.appId", "")}\"")
        buildConfigField(
            "String",
            "AGORA_APP_CERTIFICATE",
            "\"${envProperties.getProperty("agora.appCertificate", "")}\""
        )
        buildConfigField("String", "REST_KEY", "\"${envProperties.getProperty("agora.restKey", "")}\"")
        buildConfigField("String", "REST_SECRET", "\"${envProperties.getProperty("agora.restSecret", "")}\"")
        buildConfigField("String", "PIPELINE_ID", "\"${envProperties.getProperty("agora.pipelineId", "")}\"")
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
//    implementation(project(":lib"))

    implementation("io.alienzh.convoai:convoai-api:1.0.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)
    implementation("io.agora.rtc:full-sdk:4.5.2") {
        exclude(group = "io.agora.infra", module = "aosl")
    }
    implementation("io.agora:agora-rtm-lite:2.2.4") {
        exclude(group = "io.agora.infra", module = "aosl")
    }

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle components
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)

    // RecyclerView
    implementation(libs.androidx.recyclerview)

    // Navigation Component
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)


}