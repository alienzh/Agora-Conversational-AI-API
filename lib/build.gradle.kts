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

// Maven Publishing Configuration
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = libraryGroupId
                artifactId = libraryArtifactId
                version = libraryVersion
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

        repositories {
            maven {
                name = "Sonatype"
                val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                url = uri(if (libraryVersion.endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
                credentials {
                    username = project.findProperty("sonatypeUsername") as String? ?: System.getenv("SONATYPE_USERNAME")
                    password = project.findProperty("sonatypePassword") as String? ?: System.getenv("SONATYPE_PASSWORD")
                }
            }
            
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/alienzh/Agora-Conversational-AI-API")
                credentials {
                    username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR") ?: "alienzh"
                    password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }

    // GPG Signing
    signing {
        val keyId = project.findProperty("signing.keyId") as String?
        val password = project.findProperty("signing.password") as String?
        val secretKeyRingFile = project.findProperty("signing.secretKeyRingFile") as String? ?: "secret.gpg"
        
        if (keyId != null && password != null) {
            val secretKeyFile = project.file(secretKeyRingFile).takeIf { it.exists() }
                ?: project.rootProject.file(secretKeyRingFile).takeIf { it.exists() }
            
            secretKeyFile?.let {
                signing.useInMemoryPgpKeys(keyId, it.readText(), password)
                sign(publishing.publications["release"])
            }
        }
    }
}

// Publish Commands:
// Maven Central: ./gradlew :lib:publishReleasePublicationToSonatypeRepository
// GitHub Packages: ./gradlew :lib:publishReleasePublicationToGitHubPackagesRepository
// Local Test: ./gradlew :lib:publishToMavenLocal