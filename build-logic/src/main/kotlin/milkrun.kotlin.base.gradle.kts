// Applied by every Kotlin module: formatting + common compiler flags.
plugins {
    id("com.diffplug.spotless")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.version("ktlint"))
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.version("ktlint"))
    }
}
