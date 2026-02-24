package com.ivan_martinez.server

import com.ivan_martinez.common.*
import kotlinx.serialization.encodeToString

class PvpSession(
    private val size: Int,
    private val a: Side,
    private val b: Side
) {
    data class Side(
        val name: String,
        val send: (type: String, payloadJson: String) -> Unit,
        val ownBoard: BattleshipBoard,    // Mis barcos (para mostrar al jugador)
        val targetBoard: BattleshipBoard  // Tablero donde el rival dispara (mis barcos vistos como “objetivo”)
    )

    private var turnA = true

    private val statsA = Stats()
    private val statsB = Stats()

    data class Stats(var shots: Int = 0, var hits: Int = 0, var turns: Int = 0)

    fun start(json: kotlinx.serialization.json.Json) {
        // Enviamos a cada uno: rival + tamaño + MIS barcos (para render)
        a.send("PVP_MATCH_FOUND", json.encodeToString(
            MatchFound(opponentName = b.name, boardSize = size, myShips = placements(a.ownBoard))
        ))
        b.send("PVP_MATCH_FOUND", json.encodeToString(
            MatchFound(opponentName = a.name, boardSize = size, myShips = placements(b.ownBoard))
        ))

        // Turno inicial
        broadcastTurn(json)
    }

    /**
     * Procesa un ataque del jugador 'attackerName'. Devuelve:
     * - null si la partida sigue
     * - nombre del ganador si termina
     */
    fun attack(attackerName: String, position: String, json: kotlinx.serialization.json.Json): String? {
        val attackerIsA = attackerName == a.name
        if (attackerIsA != turnA) {
            // No es su turno
            val who = if (attackerIsA) a else b
            who.send("ERROR", json.encodeToString(ErrorMessage("No es tu turno")))
            return null
        }

        val attacker = if (attackerIsA) a else b
        val defender = if (attackerIsA) b else a
        val st = if (attackerIsA) statsA else statsB

        val rc = PositionCodec.parse(position, size)
        if (rc == null) {
            attacker.send("ERROR", json.encodeToString(ErrorMessage("Posición inválida: $position")))
            return null
        }
        val (r, c) = rc

        st.shots++
        st.turns++

        val out = defender.targetBoard.attack(r, c)
        if (out.hit) st.hits++

        val res = AttackResult(
            by = Attacker.PLAYER, // el cliente lo interpretará como YOU/OPPONENT según el canal
            position = PositionCodec.format(r, c),
            result = if (out.hit) ShotResult.HIT else ShotResult.MISS,
            sunk = out.sunk,
            sunkShip = out.sunkShip
        )

        // Enviar resultado a ambos, con tipo distinto para que el cliente sepa si es “mi tiro” o “tiro rival”
        attacker.send("PVP_ATTACK_RESULT_YOU", json.encodeToString(res))
        defender.send("PVP_ATTACK_RESULT_OPP", json.encodeToString(res))

        // ¿Fin?
        if (defender.targetBoard.allShipsSunk()) {
            // ganador = atacante
            attacker.send("PVP_GAME_OVER", json.encodeToString(GameOver(Attacker.PLAYER, "ALL_SHIPS_SUNK")))
            defender.send("PVP_GAME_OVER", json.encodeToString(GameOver(Attacker.AI, "ALL_SHIPS_SUNK")))
            return attacker.name
        }

        // Cambiar turno
        turnA = !turnA
        broadcastTurn(json)
        return null
    }

    fun statsFor(name: String): Stats {
        return if (name == a.name) statsA else statsB
    }

    private fun broadcastTurn(json: kotlinx.serialization.json.Json) {
        val whoA = if (turnA) PvpWho.YOU else PvpWho.OPPONENT
        val whoB = if (turnA) PvpWho.OPPONENT else PvpWho.YOU
        a.send("PVP_TURN", json.encodeToString(PvpTurn(whoA)))
        b.send("PVP_TURN", json.encodeToString(PvpTurn(whoB)))
    }

    private fun placements(board: BattleshipBoard): List<ShipPlacement> {
        return board.placementsAsPositions().map { (ship, cells) ->
            ShipPlacement(ship, cells.map { (r, c) -> PositionCodec.format(r, c) })
        }
    }
}