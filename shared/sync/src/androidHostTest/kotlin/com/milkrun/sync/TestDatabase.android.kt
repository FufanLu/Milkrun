package com.milkrun.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.milkrun.database.MilkrunDatabase
import com.milkrun.database.openMilkrunDatabase
import java.util.Properties

internal actual fun testDatabase(): MilkrunDatabase =
    openMilkrunDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), MilkrunDatabase.Schema))
