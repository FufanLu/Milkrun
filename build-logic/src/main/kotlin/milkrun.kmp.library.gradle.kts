// Convention for every module under shared/: one Kotlin source set, four targets.
//   android            -> consumed by android/app
//   iosArm64 / iosSimulatorArm64 -> exported through :shared:kit as MilkrunKit.xcframework
//   jvm                -> consumed by server/ and tools/, and the fastest place to run commonTest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("milkrun.kotlin.base")
}

kotlin {
    jvmToolchain(libs.version("jvm-toolchain").toInt())
    explicitApi()

    android {
        namespace = milkrunNamespace
        compileSdk = libs.version("android-compileSdk").toInt()
        minSdk = libs.version("android-minSdk").toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest { }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
        optIn.addAll("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
