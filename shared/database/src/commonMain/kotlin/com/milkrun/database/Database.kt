package com.milkrun.database

import app.cash.sqldelight.db.SqlDriver

/** Opens [MilkrunDatabase] on a platform driver. Platform entry points: `AndroidDatabase`, `IosDatabase`, `JvmDatabase`. */
public fun openMilkrunDatabase(driver: SqlDriver): MilkrunDatabase = MilkrunDatabase(driver)
