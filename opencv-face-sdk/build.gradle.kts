import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.PublishingExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.hntek.android.opencv_face_sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        version = "1.0.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    // OpenCV依赖
    implementation(project(":opencv"))

    // AndroidX依赖
    implementation(libs.androidx.core.ktx)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

afterEvaluate {
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.hntek.android"
                artifactId = "opencv-aar"
                version = "1.0.0.0"
            }
        }

        repositories {
            maven {
                name = "云效制品"
                url = uri("https://packages.aliyun.com/5fe195deedbcb529c62883a6/maven/repo-zahfn")

                credentials {
                    username = project.findProperty("MAVEN_USERNAME") as String? ?: ""
                    password = project.findProperty("MAVEN_PASSWORD") as String? ?: ""
                }
            }
        }
    }
}