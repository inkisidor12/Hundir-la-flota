package com.ivan_martinez.common


import kotlinx.serialization.Serializable
import com.ivan_martinez.common.PositionCodec
@Serializable
data class PlacementConfig(val ships: List<ShipPlacement>)

/**
 * Validación de colocación:
 * - barcos esperados (1x5, 2x4, 3x3, 4x2)
 * - posiciones dentro del tablero
 * - en línea recta y contiguas
 * - sin solapes
 */
object PlacementValidator {

    private val shipSizes = mapOf(
        ShipType.CARRIER to 5,
        ShipType.BATTLESHIP to 4,
        ShipType.CRUISER to 3,
        ShipType.DESTROYER to 2
    )

    private val shipCounts = mapOf(
        ShipType.CARRIER to 1,
        ShipType.BATTLESHIP to 2,
        ShipType.CRUISER to 3,
        ShipType.DESTROYER to 4
    )

    fun validate(size: Int, placement: PlacementConfig): String? {
        // 1) Conteo de barcos por tipo
        val counts = placement.ships.groupingBy { it.ship }.eachCount()
        for ((type, expected) in shipCounts) {
            val got = counts[type] ?: 0
            if (got != expected) return "Conteo inválido para $type: esperado=$expected, recibido=$got"
        }

        // 2) Validar cada barco y solapes
        val occupied = HashSet<Pair<Int, Int>>()

        for (sp in placement.ships) {
            val expectedLen = shipSizes[sp.ship] ?: return "Tipo de barco desconocido: ${sp.ship}"
            if (sp.positions.size != expectedLen) {
                return "Longitud inválida para ${sp.ship}: esperado=$expectedLen, recibido=${sp.positions.size}"
            }

            val cells = sp.positions.mapNotNull { PositionCodec.parse(it, size) }
            if (cells.size != sp.positions.size) return "Posición fuera de tablero en ${sp.ship}: ${sp.positions}"

            // línea recta
            val rows = cells.map { it.first }.toSet()
            val cols = cells.map { it.second }.toSet()
            val straight = (rows.size == 1 && cols.size == expectedLen) || (cols.size == 1 && rows.size == expectedLen)
            if (!straight) return "Barco ${sp.ship} no está en línea recta: ${sp.positions}"

            // contiguo
            val sorted = if (rows.size == 1) cells.sortedBy { it.second } else cells.sortedBy { it.first }
            for (i in 1 until sorted.size) {
                val (r1, c1) = sorted[i - 1]
                val (r2, c2) = sorted[i]
                val ok = (r1 == r2 && c2 == c1 + 1) || (c1 == c2 && r2 == r1 + 1)
                if (!ok) return "Barco ${sp.ship} no es contiguo: ${sp.positions}"
            }

            // solapes
            for (cell in cells) {
                if (!occupied.add(cell)) return "Solape detectado en celda ${PositionCodec.format(cell.first, cell.second)}"
            }
        }

        return null // OK
    }
}