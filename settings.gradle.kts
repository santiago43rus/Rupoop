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
        mavenLocal()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
        maven { url = uri("https://jcenter.bintray.com") }
    }
}

rootProject.name = "Rupoop"
include(":app")
