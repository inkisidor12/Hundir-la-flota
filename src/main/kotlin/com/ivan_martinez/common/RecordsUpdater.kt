package com.ivan_martinez.common


object RecordsUpdater {

    fun ensurePlayer(records: Records, name: String): PlayerStats {
        return records.players.getOrPut(name) { PlayerStats() }
    }

    fun applyPveResult(
        stats: PlayerStats,
        won: Boolean,
        shots: Int,
        hits: Int,
        turnsToWin: Int?
    ) {
        // W/L
        if (won) stats.pveWins++ else stats.pveLosses++

        // Rachas (solo cuentan en victorias)
        if (won) {
            stats.currentWinStreak++
            if (stats.currentWinStreak > stats.bestWinStreak) {
                stats.bestWinStreak = stats.currentWinStreak
            }
        } else {
            stats.currentWinStreak = 0
        }

        // Precisión
        stats.shots += shots
        stats.hits += hits

        // Partida más rápida (solo si ganaste)
        if (won && turnsToWin != null) {
            val prev = stats.fastestWinTurns
            if (prev == null || turnsToWin < prev) {
                stats.fastestWinTurns = turnsToWin
            }
        }
    }
}
