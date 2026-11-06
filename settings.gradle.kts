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
    }
}

rootProject.name = "Hush"
include(":app", ":link", ":wear", ":tv")
// Shared UI kit and the device-module contract; every brand is a module under devices/.
include(":ui", ":devices:api", ":devices:sony-core", ":devices:sony", ":devices:apple", ":devices:jabra")
// Wire protocols as plain Kotlin: frames, commands and parsers, tested on the JVM with no Android or UI.
include(":protocol:mdr", ":protocol:gnp")
