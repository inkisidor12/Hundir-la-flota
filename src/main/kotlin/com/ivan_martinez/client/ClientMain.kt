package com.ivan_martinez.client

import com.ivan_martinez.common.ConfigLoader
import com.ivan_martinez.common.Protocol
import com.ivan_martinez.common.Records
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

fun main() {
    val config = ConfigLoader.loadServerConfig()

    Socket(config.host, config.port).use { socket ->
        val input = BufferedReader(InputStreamReader(socket.getInputStream()))
        val output = PrintWriter(socket.getOutputStream(), true)

        // 1) Esperamos el primer mensaje RECORDS del servidor
        val firstLine = input.readLine() ?: error("Servidor cerró la conexión")
        val (type, payload) = Protocol.decode(firstLine)
        require(type == "RECORDS") { "Se esperaba RECORDS y llegó $type" }

        val records = json.decodeFromString<Records>(payload)
        println(" Records sincronizados. Jugadores: ${records.players.size}")

        // 2) Menú
        menuLoop(records, output, input)
    }
}

private fun menuLoop(records: Records, output: PrintWriter, input: BufferedReader) {
    while (true) {
        println()
        println("=== HUNDIR LA FLOTA ONLINE ===")
        println("1) Nueva Partida PVP (no implementado aún)")
        println("2) Nueva Partida PVE (no implementado aún)")
        println("3) Ver Records")
        println("4) Configuración (no implementado aún)")
        println("5) Salir")
        print("Opción: ")

        when (readLine()?.trim()) {
            "1" -> println(" PVP aún no implementado")
            "2" -> println(" PVE aún no implementado")
            "3" -> showRecords(records)
            "4" -> println("3 Configuración aún no implementado")
            "5" -> {
                println("Saliendo...")
                // opcional: avisar al servidor
                output.println("SALIR")
                input.readLine() // BYE si el server lo envía
                return
            }
            else -> println("Opción inválida")
        }
    }
}

private fun showRecords(records: Records) {
    println()
    println("=== RECORDS ===")
    if (records.players.isEmpty()) {
        println("No hay jugadores aún.")
        return
    }

    records.players.forEach { (name, stats) ->
        println("- $name | PVP: ${stats.pvpWins}W/${stats.pvpLosses}L | PVE: ${stats.pveWins}W/${stats.pveLosses}L | Best streak: ${stats.bestWinStreak} | Accuracy: ${"%.2f".format(stats.accuracy() * 100)}%")
    }
}
