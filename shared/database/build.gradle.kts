plugins {
    id("milkrun.kmp.library")
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.runtime)
            api(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.driver.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.driver.native)
        }
        jvmMain.dependencies {
            api(libs.sqldelight.driver.sqlite)
        }
    }
}

sqldelight {
    databases {
        create("MilkrunDatabase") {
            packageName.set("com.milkrun.database")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            migrationOutputDirectory.set(file("build/sqldelight/migrations"))
            verifyMigrations.set(true)
        }
    }
}
