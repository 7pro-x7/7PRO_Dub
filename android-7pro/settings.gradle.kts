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
        // The Jitsi Meet Android SDK (org.jitsi.react:jitsi-meet-sdk) is published
        // to Jitsi's own Maven repository, not to Google/Maven Central.
        maven { url = uri("https://github.com/jitsi/jitsi-maven-repository/raw/master/releases") }
    }
}
rootProject.name = "7PRO"
include(":app")
