pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/nexus/content/groups/public/") }
        maven { url = uri("https://maven.aliyun.com/nexus/content/repositories/jcenter") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/nexus/content/groups/public/") }
        maven { url = uri("https://maven.aliyun.com/nexus/content/repositories/jcenter") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven {
            url = uri("https://packages.aliyun.com/5fe195deedbcb529c62883a6/maven/repo-zahfn")
            credentials {
                username = providers.gradleProperty("MAVEN_USERNAME").orElse("").get()
                password = providers.gradleProperty("MAVEN_PASSWORD").orElse("").get()
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "OpenCV"
include(":app")
include(":opencv")
include(":opencv-face-sdk")
