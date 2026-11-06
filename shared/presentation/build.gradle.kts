plugins {
    id("milkrun.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.network)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        val androidHostTest by getting {
            dependencies { implementation(libs.sqldelight.driver.sqlite) }
        }
    }
}
