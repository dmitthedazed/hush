package com.occaecat.hush.backend

import kotlinx.coroutines.Deferred

/** A single protocol session. Closing it must also cancel reads, writes and pending confirmations. */
interface HeadphoneBackend {
    val closed: Deferred<Throwable?>
    suspend fun open()
    suspend fun initialize()
    fun close()
}
