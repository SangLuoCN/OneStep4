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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "Aliucord"
            url = uri("https://maven.aliucord.com/releases")
            content {
                includeGroup("com.aliucord")
                includeGroup("com.aliucord.lsplant")
            }
        }
    }
}

rootProject.name = "OneStep4.0"
include(":app")
include(":xposed-api-stubs")
