package com.milkrun.server.sync

class InMemorySyncStoreTest : SyncStoreContract() {
    override fun newStore(): SyncStore = InMemorySyncStore()
}
