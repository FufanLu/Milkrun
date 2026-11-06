package com.milkrun.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

public object AndroidDatabase {
    public fun create(context: Context, name: String = "milkrun.db"): MilkrunDatabase {
        val driver =
            AndroidSqliteDriver(
                schema = MilkrunDatabase.Schema,
                context = context,
                name = name,
                callback =
                object : AndroidSqliteDriver.Callback(MilkrunDatabase.Schema) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        db.enableWriteAheadLogging()
                    }
                },
            )
        return openMilkrunDatabase(driver)
    }
}
