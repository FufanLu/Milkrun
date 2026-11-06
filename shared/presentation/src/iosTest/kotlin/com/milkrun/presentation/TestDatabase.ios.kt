package com.milkrun.presentation

import com.milkrun.database.IosDatabase
import com.milkrun.database.MilkrunDatabase

internal actual fun testDatabase(): MilkrunDatabase = IosDatabase.inMemory()
