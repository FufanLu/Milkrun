pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Milkrun"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":shared:model")
include(":shared:protocol")
include(":shared:database")
include(":shared:sync")
include(":shared:network")
include(":server:core")
include(":server:app")
include(":tools:simulation")
