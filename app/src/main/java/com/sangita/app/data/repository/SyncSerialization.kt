package com.sangita.app.data.repository

import kotlinx.coroutines.sync.Mutex

/** Serializes server snapshots and outbox flushes so a stale snapshot cannot undo a just-synced change. */
internal object SyncSerialization {
    val mutex = Mutex()
}
