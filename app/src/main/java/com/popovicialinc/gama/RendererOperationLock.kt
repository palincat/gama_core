package com.popovicialinc.gama

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes renderer writes from the activity, automation, tile, and widget. */
object RendererOperationLock {
    private val mutex = Mutex()
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}
