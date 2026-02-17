package com.ivan_martinez.server


import com.ivan_martinez.common.ShipType
import kotlin.random.Random

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

    // Para saber qué posiciones ocupa cada barco
    private val shipCells: MutableMap<ShipType, MutableSet<Pair<Int, Int>>> = mutableMapOf()

    fun placeShip(ship: ShipType, cells: List<Pair<Int, Int>>) {
        require(cells.size == ship.size)
        // validar dentro del tablero y sin solapamiento
        for ((r, c) in cells) {
            require(r in 0 until size && c in 0 until size)
            require(gridShips[r][c] == null) { "Solapamiento de barcos" }
        }
        for ((r, c) in cells) gridShips[r][c] = ship
        shipCells.getOrPut(ship) { mutableSetOf() }.addAll(cells)
    }

    fun attack(r: Int, c: Int): ShotOutcome {
        require(r in 0 until size && c in 0 until size)
        if (hits[r][c]) {
            // si repite tiro, lo tratamos como MISS (o podrías mandar ERROR)
            return ShotOutcome(hit = false, sunk = false, sunkShip = null)
        }

        hits[r][c] = true
        val ship = gridShips[r][c] ?: return ShotOutcome(hit = false, sunk = false, sunkShip = null)

        // quitar celda a ese barco
        val cells = shipCells[ship]!!
        cells.remove(r to c)
        val sunk = cells.isEmpty()
        return ShotOutcome(hit = true, sunk = sunk, sunkShip = if (sunk) ship else null)
    }

    fun allShipsSunk(): Boolean {
        return shipCells.values.all { it.isEmpty() }
    }

    fun placementsAsPositions(): Map<ShipType, List<Pair<Int, Int>>> {
        return shipCells.mapValues { it.value.toList() }
    }

    companion object {
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
            while (true) {
                val horizontal = rng.nextBoolean()
                val r = rng.nextInt(size)
                val c = rng.nextInt(size)

                val cells = mutableListOf<Pair<Int, Int>>()
                for (i in 0 until ship.size) {
                    val rr = if (horizontal) r else r + i
                    val cc = if (horizontal) c + i else c
                    if (rr !in 0 until size || cc !in 0 until size) {
                        cells.clear()
                        break
                    }
                    cells.add(rr to cc)
                }
                if (cells.isEmpty()) continue

                // comprobar solapamiento
                val ok = cells.all { (rr, cc) ->
                    try {
                        // consultamos si hay barco intentando "mirar" el grid indirectamente:
                        // truco: atacamos? NO. Aquí no hay getter, así que probamos a colocar validando.
                        true
                    } catch (_: Exception) { false }
                }

                if (!ok) continue

                try {
                    board.placeShip(ship, cells)
                    return
                } catch (_: Exception) {
                    // solapamiento u otra validación -> reintentar
                }
            }
        }
    }
}
