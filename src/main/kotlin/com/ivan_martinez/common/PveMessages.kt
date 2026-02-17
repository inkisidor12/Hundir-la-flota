package com.ivan_martinez.common


import kotlinx.serialization.Serializable

@Serializable
data class NewGamePveRequest(val settings: ClientSettings)

@Serializable
data class ShipPlacement(val ship: ShipType, val positions: List<String>)

@Serializable
data class GameStarted(
    val boardSize: Int,
    val myShips: List<ShipPlacement>
)

@Serializable
data class AttackRequest(val position: String)

@Serializable
data class AttackResult(
    val by: Attacker,
    val position: String,
    val result: ShotResult,
    val sunk: Boolean = false,
    val sunkShip: ShipType? = null
)

@Serializable
data class TurnMessage(val who: Attacker)

@Serializable
data class GameOver(val winner: Attacker, val reason: String)

@Serializable
enum class ShotResult { HIT, MISS }

@Serializable
enum class Attacker { PLAYER, AI }

@Serializable
enum class ShipType(val size: Int) {
    CARRIER(5),
    BATTLESHIP(4),
    CRUISER(3),
    DESTROYER(2)
}

