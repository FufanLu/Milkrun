import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

// The one binary the iOS app links: every shared module, exported under one Swift module name.
// SKIE turns Flow into AsyncSequence, suspend into async, and sealed classes into Swift enums.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.skie)
    id("milkrun.kotlin.base")
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
    val xcf = XCFramework("MilkrunKit")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "MilkrunKit"
            isStatic = true
            export(projects.shared.model)
            export(projects.shared.protocol)
            export(projects.shared.sync)
            export(projects.shared.presentation)
            export(projects.shared.proof)
            linkerOpts("-lsqlite3")
            xcf.add(this)
        }
    }
    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlinx.cinterop.ExperimentalForeignApi")
    }
    sourceSets {
        iosMain.dependencies {
            api(projects.shared.model)
            api(projects.shared.protocol)
            api(projects.shared.sync)
            api(projects.shared.presentation)
            api(projects.shared.proof)
            implementation(libs.ktor.client.darwin)
        }
    }
}
