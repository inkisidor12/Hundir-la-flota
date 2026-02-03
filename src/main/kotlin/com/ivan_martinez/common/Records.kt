package com.ivan_martinez.common


import kotlinx.serialization.Serializable

@Serializable
data class Records(
    val players: MutableMap<String, PlayerStats> = mutableMapOf()
)

@Serializable
data class PlayerStats(
    var pvpWins: Int = 0,
    var pvpLosses: Int = 0,
    var pveWins: Int = 0,
    var pveLosses: Int = 0,
    var bestWinStreak: Int = 0,
    var currentWinStreak: Int = 0,

    // Hundir la flota: precisión = hits/shots
    var shots: Int = 0,
    var hits: Int = 0,

    // Partida más rápida (menos turnos)
    var fastestWinTurns: Int? = null
) {
    fun accuracy(): Double = if (shots == 0) 0.0 else hits.toDouble() / shots.toDouble()
}
