package com.milkrun.server

import com.milkrun.server.sync.SyncStore
import com.milkrun.server.sync.SyncStoreContract

/** The same contract the in-memory store passes, against a real PostgreSQL 17. */
class PostgresSyncStoreTest : SyncStoreContract() {
    override fun newStore(): SyncStore = PostgresSyncStore(Postgres.dataSource)
}
