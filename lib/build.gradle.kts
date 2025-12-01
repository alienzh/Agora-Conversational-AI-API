import java.io.*
import java.net.*
import java.util.*

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
    signing
}

android {
    namespace = "io.agora.convoai.convoaiApi"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Use compileOnly so dependencies are not transitive; users must add RTC and RTM themselves
    // Recommended: use agora-rtm-lite in App to avoid libaosl.so conflicts
    compileOnly(libs.agora.rtc)
    compileOnly(libs.agora.rtm)
    implementation(libs.gson)
}

// Maven Publishing Configuration
// Library version information
val libraryGroupId = "io.github.alienzh"
val libraryArtifactId = "convoai-api"
val libraryVersion = "1.0.0" // Change version here as needed

// Get Bearer Token from gradle.properties or environment variable
val mavenAuthorization = project.findProperty("mavenAuthorization") as String?
    ?: System.getenv("MAVEN_AUTHORIZATION")
    ?: ""

// Get GPG signing configuration from gradle.properties or environment variables
val signingKeyId = project.findProperty("signing.keyId") as String?
    ?: System.getenv("GPG_KEY_ID")
    ?: ""
val signingPassword = project.findProperty("signing.password") as String?
    ?: System.getenv("GPG_PASSWORD")
    ?: ""
val signingSecretKeyRingFile = project.findProperty("signing.secretKeyRingFile") as String?
    ?: System.getenv("GPG_SECRET_KEY_RING_FILE")
    ?: "secret.gpg"

afterEvaluate {
    publishing {
        repositories {
            maven {
                name = "LocalRepo"
                url = uri("${rootProject.projectDir}/repo")
            }
        }
        
        publications {
            create<MavenPublication>("release") {
                groupId = libraryGroupId
                artifactId = libraryArtifactId
                version = libraryVersion
                
                // Release build variant (includes AAR, Sources JAR, Javadoc JAR if configured)
                from(components["release"])

                pom {
                    name.set("Agora Conversational AI API")
                    description.set("Android library for Agora Conversational AI API integration")
                    url.set("https://github.com/alienzh/Agora-Conversational-AI-API")
                    
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    
                    developers {
                        developer {
                            id.set("alienzh")
                            name.set("alienzh")
                        }
                    }
                    
                    scm {
                        connection.set("scm:git:git://github.com/alienzh/Agora-Conversational-AI-API.git")
                        developerConnection.set("scm:git:ssh://github.com:alienzh/Agora-Conversational-AI-API.git")
                        url.set("https://github.com/alienzh/Agora-Conversational-AI-API")
                    }
                }
            }
        }
    }
    
    // GPG Signing Configuration (Required for Maven Central)
    signing {
        if (signingKeyId.isNotBlank() && signingPassword.isNotBlank()) {
            val secretKeyFile = project.file(signingSecretKeyRingFile).takeIf { it.exists() }
                ?: project.rootProject.file(signingSecretKeyRingFile).takeIf { it.exists() }
            
            if (secretKeyFile != null) {
                val keyContent = secretKeyFile.readText()
                val fullKeyId = signingKeyId.trim().uppercase()
                val shortKeyId = if (fullKeyId.length > 8) fullKeyId.takeLast(8) else fullKeyId
                
                try {
                    signing.useInMemoryPgpKeys(shortKeyId, keyContent, signingPassword)
                    sign(publishing.publications["release"])
                } catch (e: Exception) {
                    throw GradleException("Failed to configure GPG signing: ${e.message}", e)
                }
            } else {
                throw GradleException("GPG secret key file not found at: $signingSecretKeyRingFile. Maven Central requires GPG signatures.")
            }
        } else {
            throw GradleException("GPG signing not configured. Maven Central requires GPG signatures for all files.")
        }
    }
    
    // Task to delete old folders
    tasks.register("deleteFolders", Delete::class) {
        delete("${rootProject.projectDir}/published")
        delete("${rootProject.projectDir}/repo")
    }
    
    // Task to zip the repo folder and upload to Central Portal
    tasks.register("zipFolderAndUpload", Zip::class) {
        from("${rootProject.projectDir}/repo")
        archiveFileName.set("${libraryArtifactId}-${libraryVersion}.zip")
        
        // Exclude maven-metadata files
        exclude("**/maven-metadata*")
        
        destinationDirectory.set(file("${rootProject.projectDir}/published"))
        
        doLast {
            val location = "https://central.sonatype.com/api/v1/publisher/upload?name=${libraryGroupId}:${libraryArtifactId}:${libraryVersion}&publishingType=USER_MANAGED"
            val zipFile = file("${rootProject.projectDir}/published/${libraryArtifactId}-${libraryVersion}.zip")
            
            if (!zipFile.exists()) {
                throw GradleException("Zip file not found: ${zipFile.absolutePath}")
            }
            
            if (mavenAuthorization.isBlank()) {
                throw GradleException("mavenAuthorization is required. Set it in gradle.properties or MAVEN_AUTHORIZATION environment variable")
            }
            
            // Use curl for upload to handle multipart/form-data correctly
            exec {
                commandLine(
                    "curl",
                    "--request", "POST",
                    "--url", location,
                    "--header", "Authorization: Bearer $mavenAuthorization",
                    "--form", "bundle=@${zipFile.absolutePath}",
                    "--verbose"
                )
            }
        }
    }
    
    // Configure task dependencies
    tasks.named("publish").configure {
        dependsOn("deleteFolders")
        finalizedBy("zipFolderAndUpload")
    }
}

// Publish Commands:
// Maven Central (via Central Portal): ./gradlew :lib:publish
// Local Test: ./gradlew :lib:publishToMavenLocal