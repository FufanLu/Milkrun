package com.milkrun.sync

import com.milkrun.database.MilkrunDatabase

/** A fresh in-memory database on the platform's own SQLite driver. */
internal expect fun testDatabase(): MilkrunDatabase
