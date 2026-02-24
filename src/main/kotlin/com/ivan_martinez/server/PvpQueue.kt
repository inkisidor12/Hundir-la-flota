package com.ivan_martinez.server


import com.ivan_martinez.common.ClientSettings
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class WaitingPvpPlayer(
    val name: String,
    val settings: ClientSettings,
    val send: (type: String, payloadJson: String) -> Unit,
    val attachSession: (PvpSession) -> Unit
)

object PvpQueue {
    private val lock = ReentrantLock()
    private var waiting: WaitingPvpPlayer? = null

    /**
     * Devuelve el rival si había alguien esperando, o null si el jugador queda en cola.
     */
    fun enqueueOrMatch(me: WaitingPvpPlayer): WaitingPvpPlayer? = lock.withLock {
        val other = waiting
        return if (other == null) {
            waiting = me
            null
        } else {
            waiting = null
            other
        }
    }

    fun cancelIfWaiting(name: String) = lock.withLock {
        if (waiting?.name == name) waiting = null
    }
}