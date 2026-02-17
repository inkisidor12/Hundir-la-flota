package com.ivan_martinez.server


import com.ivan_martinez.common.*
import kotlin.random.Random

data class PveStats(
    val playerShots: Int,
    val playerHits: Int,
    val playerTurns: Int
)


class PveSession(val size: Int) {
    private val playerBoard = BattleshipBoard.randomBoard(size)
    private val aiBoard = BattleshipBoard.randomBoard(size)

    private val aiTried = mutableSetOf<Pair<Int, Int>>()
    private val rng = Random.Default

    private var playerShots = 0
    private var playerHits = 0
    private var playerTurns = 0

    fun statsSnapshot(): PveStats = PveStats(playerShots, playerHits, playerTurns)

    fun gameStartedPayload(): GameStarted {
        val myShips = playerBoard.placementsAsPositions().map { (ship, cells) ->
            ShipPlacement(
                ship = ship,
                positions = cells.map { (r, c) -> PositionCodec.format(r, c) }
            )
        }
        return GameStarted(boardSize = size, myShips = myShips)
    }

    fun playerAttack(pos: String): List<Any> {
        val rc = PositionCodec.parse(pos, size)
            ?: return listOf(ErrorMessage("Posición inválida: $pos"))

        val (r, c) = rc
        val out = aiBoard.attack(r, c)
        val res = AttackResult(
            by = Attacker.PLAYER,
            position = PositionCodec.format(r, c),
            result = if (out.hit) ShotResult.HIT else ShotResult.MISS,
            sunk = out.sunk,
            sunkShip = out.sunkShip
        )

        val messages = mutableListOf<Any>()
        messages.add(res)

        if (aiBoard.allShipsSunk()) {
            messages.add(GameOver(winner = Attacker.PLAYER, reason = "ALL_SHIPS_SUNK"))
            return messages
        }

        playerShots++
        playerTurns++
        if (out.hit) playerHits++

        // Turno IA
        val aiMove = aiAttack()
        messages.add(aiMove)

        if (playerBoard.allShipsSunk()) {
            messages.add(GameOver(winner = Attacker.AI, reason = "ALL_SHIPS_SUNK"))
            return messages
        }

        messages.add(TurnMessage(Attacker.PLAYER))
        return messages
    }

    private fun aiAttack(): AttackResult {
        var rc: Pair<Int, Int>
        do {
            rc = rng.nextInt(size) to rng.nextInt(size)
        } while (!aiTried.add(rc))

        val (r, c) = rc
        val out = playerBoard.attack(r, c)
        return AttackResult(
            by = Attacker.AI,
            position = PositionCodec.format(r, c),
            result = if (out.hit) ShotResult.HIT else ShotResult.MISS,
            sunk = out.sunk,
            sunkShip = out.sunkShip
        )
    }

}
