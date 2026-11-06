package com.milkrun.database

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.inMemoryDriver

public object IosDatabase {
    public fun create(name: String = "milkrun.db"): MilkrunDatabase =
        openMilkrunDatabase(NativeSqliteDriver(MilkrunDatabase.Schema, name))

    public fun inMemory(): MilkrunDatabase = openMilkrunDatabase(inMemoryDriver(MilkrunDatabase.Schema))
}
