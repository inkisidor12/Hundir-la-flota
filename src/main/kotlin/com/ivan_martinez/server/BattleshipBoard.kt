package com.ivan_martinez.server

import com.ivan_martinez.common.PlacementConfig
import com.ivan_martinez.common.ShipType
import kotlin.random.Random
import com.ivan_martinez.common.PositionCodec

data class ShotOutcome(
    val hit: Boolean,
    val sunk: Boolean,
    val sunkShip: ShipType? = null
)

class BattleshipBoard(private val size: Int) {

    // gridShips[r][c] = tipo de barco si hay barco en esa celda
    private val gridShips: Array<Array<ShipType?>> =
        Array(size) { arrayOfNulls<ShipType>(size) }

    // hits[r][c] = si esa celda ha sido atacada
    private val hits: Array<BooleanArray> =
        Array(size) { BooleanArray(size) { false } }

    // ✅ Posiciones originales de cada barco (NO se modifican)
    private val originalShipCells: MutableMap<ShipType, MutableSet<Pair<Int, Int>>> = mutableMapOf()

    // ✅ Posiciones restantes “vivas” para saber cuándo se hunde (estas sí se modifican)
    private val remainingShipCells: MutableMap<ShipType, MutableSet<Pair<Int, Int>>> = mutableMapOf()

    fun placeShip(ship: ShipType, cells: List<Pair<Int, Int>>) {
        val expectedSize = shipSize(ship)
        require(cells.size == expectedSize) { "Tamaño inválido para $ship: esperado=$expectedSize recibido=${cells.size}" }

        // validar dentro del tablero y sin solapamiento
        for ((r, c) in cells) {
            require(r in 0 until size && c in 0 until size) { "Celda fuera de tablero: $r,$c" }
            require(gridShips[r][c] == null) { "Solapamiento de barcos en $r,$c" }
        }

        for ((r, c) in cells) gridShips[r][c] = ship

        originalShipCells.getOrPut(ship) { mutableSetOf() }.addAll(cells)
        remainingShipCells.getOrPut(ship) { mutableSetOf() }.addAll(cells)
    }

    fun attack(r: Int, c: Int): ShotOutcome {
        require(r in 0 until size && c in 0 until size)

        if (hits[r][c]) {
            // si repite tiro, lo tratamos como MISS (o podrías mandar ERROR)
            return ShotOutcome(hit = false, sunk = false, sunkShip = null)
        }

        hits[r][c] = true
        val ship = gridShips[r][c] ?: return ShotOutcome(hit = false, sunk = false, sunkShip = null)

        // quitar celda “viva” a ese barco
        val cells = remainingShipCells[ship]!!
        cells.remove(r to c)

        val sunk = cells.isEmpty()
        return ShotOutcome(hit = true, sunk = sunk, sunkShip = if (sunk) ship else null)
    }

    fun allShipsSunk(): Boolean {
        return remainingShipCells.values.all { it.isEmpty() }
    }

    /**
     * ✅ Para enviar al cliente la colocación inicial (siempre completa)
     */
    fun placementsAsPositions(): Map<ShipType, List<Pair<Int, Int>>> {
        return originalShipCells.mapValues { it.value.toList() }
    }

    companion object {

        fun fromPlacement(size: Int, placement: PlacementConfig): BattleshipBoard {
            val board = BattleshipBoard(size)
            for (sp in placement.ships) {
                val cells = sp.positions.map { PositionCodec.parse(it, size)!! }
                board.placeShip(sp.ship, cells)
            }
            return board
        }

        fun randomBoard(size: Int, rng: Random = Random.Default): BattleshipBoard {
            val board = BattleshipBoard(size)

            val fleet = buildList {
                add(ShipType.CARRIER)
                add(ShipType.BATTLESHIP); add(ShipType.BATTLESHIP)
                add(ShipType.CRUISER); add(ShipType.CRUISER); add(ShipType.CRUISER)
                repeat(4) { add(ShipType.DESTROYER) }
            }

            for (ship in fleet) {
                placeRandomShip(board, ship, size, rng)
            }
            return board
        }

        private fun placeRandomShip(board: BattleshipBoard, ship: ShipType, size: Int, rng: Random) {
            val len = shipSize(ship)

            while (true) {
                val horizontal = rng.nextBoolean()
                val r = rng.nextInt(size)
                val c = rng.nextInt(size)

                val cells = mutableListOf<Pair<Int, Int>>()
                for (i in 0 until len) {
                    val rr = if (horizontal) r else r + i
                    val cc = if (horizontal) c + i else c
                    if (rr !in 0 until size || cc !in 0 until size) {
                        cells.clear()
                        break
                    }
                    cells.add(rr to cc)
                }
                if (cells.isEmpty()) continue

                try {
                    board.placeShip(ship, cells)
                    return
                } catch (_: Exception) {
                    // solapamiento u otra validación -> reintentar
                }
            }
        }

        private fun shipSize(ship: ShipType): Int = when (ship) {
            ShipType.CARRIER -> 5
            ShipType.BATTLESHIP -> 4
            ShipType.CRUISER -> 3
            ShipType.DESTROYER -> 2
        }
    }
}