package com.ivan_martinez.common


object Protocol {
    const val SEP = ":"

    fun encode(type: String, json: String): String = "$type$SEP$json"

    fun decode(line: String): Pair<String, String> {
        val idx = line.indexOf(SEP)
        require(idx > 0) { "Mensaje sin separador ':'" }
        val type = line.substring(0, idx).trim()
        val json = line.substring(idx + 1).trim()
        return type to json
    }
}
