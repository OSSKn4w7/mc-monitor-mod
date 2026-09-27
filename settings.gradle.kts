pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
    }
}

rootProject.name = "McMonitorMod"

include(":core")
include(":neoforge")
include(":harness")
