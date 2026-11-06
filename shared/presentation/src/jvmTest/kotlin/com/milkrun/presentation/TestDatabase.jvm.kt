package com.milkrun.presentation

import com.milkrun.database.JvmDatabase
import com.milkrun.database.MilkrunDatabase

internal actual fun testDatabase(): MilkrunDatabase = JvmDatabase.create()
