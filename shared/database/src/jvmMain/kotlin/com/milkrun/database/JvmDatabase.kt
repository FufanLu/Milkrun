package com.milkrun.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

/** Used by the simulator and tests. `path = null` gives a private in-memory database. */
public object JvmDatabase {
    /**
     * @param wal write-ahead logging, as on Android (`AndroidDatabase` enables it). With the default
     *   rollback journal every commit rewrites the journal file and fsyncs twice.
     */
    public fun create(path: String? = null, wal: Boolean = true): MilkrunDatabase {
        val url = if (path == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:$path"
        val props = Properties()
        if (path != null && wal) {
            props["journal_mode"] = "wal"
            props["synchronous"] = "normal"
        }
        return openMilkrunDatabase(JdbcSqliteDriver(url, props, MilkrunDatabase.Schema))
    }
}
