package com.ivan_martinez.server


object PositionCodec {
    fun parse(pos: String, size: Int): Pair<Int, Int>? {
        val p = pos.trim().uppercase()
        if (p.length < 2) return null
        val colChar = p[0]
        val col = colChar - 'A'
        val rowStr = p.substring(1)
        val row = rowStr.toIntOrNull()?.minus(1) ?: return null
        if (row !in 0 until size || col !in 0 until size) return null
        return row to col
    }

    fun format(r: Int, c: Int): String = "${('A'.code + c).toChar()}${r + 1}"
}
