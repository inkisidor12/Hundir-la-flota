package com.ivan_martinez.server

import com.ivan_martinez.common.ClientSettings
import java.util.concurrent.atomic.AtomicReference

data class WaitingPlayer(
    val name: String,
    val settings: ClientSettings,
    val handler: ClientHandlerRef
)

// Referencia mínima para poder enviar mensajes al cliente desde el matchmaker
class ClientHandlerRef(
    val send: (type: String, payloadJson: String) -> Unit
)

object PvpMatchmaker {
    private val waiting = AtomicReference<WaitingPlayer?>(null)

    fun tryMatch(player: WaitingPlayer): WaitingPlayer? {
        // Si no hay nadie esperando, este queda esperando
        val prev = waiting.getAndSet(null)
        return if (prev == null) {
            waiting.set(player)
            null
        } else {
            prev
        }
    }

    fun cancelIfWaiting(name: String) {
        val cur = waiting.get()
        if (cur != null && cur.name == name) waiting.compareAndSet(cur, null)
    }
}