pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven-other.tuya.com/repository/maven-releases/") }
        maven { url = uri("https://maven-other.tuya.com/repository/maven-commercial-releases/") }
        maven { url = uri("https://maven-other.tuya.com/repository/maven-snapshots/") }
        // Tuya UI BizBundle transitive deps: jitpack (MPAndroidChart), Huawei HMS, and
        // the aliyun "public" mirror which still hosts ex-jcenter artifacts
        // (flexbox 1.1.1, recyclerview-animators 3.0.0, exoplayer 2.12.0, etc.).
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://developer.huawei.com/repo/") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "Smart Device"
include(":app")
 