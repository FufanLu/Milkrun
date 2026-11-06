// Convention for JVM deployables (server/, tools/*).
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("milkrun.kotlin.base")
}

kotlin {
    jvmToolchain(libs.version("jvm-toolchain").toInt())
    compilerOptions {
        allWarningsAsErrors.set(true)
        optIn.addAll("kotlin.time.ExperimentalTime")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
