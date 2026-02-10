package com.ivan_martinez.client

import com.ivan_martinez.common.ClientSettings
import com.ivan_martinez.common.ClientSettingsStorage
import com.ivan_martinez.common.ConfigLoader
import com.ivan_martinez.common.Difficulty
import com.ivan_martinez.common.Protocol
import com.ivan_martinez.common.Records
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.nio.file.Path
import java.nio.file.Paths

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

fun main() {
    val config = ConfigLoader.loadServerConfig()

    //  settings se cargan ANTES del menú y se pasan al menú
    val dataDir = Paths.get("data")
    dataDir.toFile().mkdirs()
    val settingsPath = dataDir.resolve("client-settings.json")
    var settings = ClientSettingsStorage.load(settingsPath)

    Socket(config.host, config.port).use { socket ->
        val input = BufferedReader(InputStreamReader(socket.getInputStream()))
        val output = PrintWriter(socket.getOutputStream(), true)

        // 1) Esperamos el primer mensaje RECORDS del servidor
        val firstLine = input.readLine() ?: error("Servidor cerró la conexión")
        val (type, payload) = Protocol.decode(firstLine)
        require(type == "RECORDS") { "Se esperaba RECORDS y llegó $type" }

        val records = json.decodeFromString<Records>(payload)
        println(" Records sincronizados. Jugadores: ${records.players.size}")

        println(" Config actual: board=${settings.boardSize}, bestOf=${settings.bestOf}, turn=${settings.turnTimeSeconds}s, diff=${settings.difficulty}")

        // 2) Menú
        menuLoop(records, output, input, settingsPath, settings)
    }
}

private fun menuLoop(
    records: Records,
    output: PrintWriter,
    input: BufferedReader,
    settingsPath: Path,
    initialSettings: ClientSettings
) {
    var settings = initialSettings

    while (true) {
        println()
        println("=== HUNDIR LA FLOTA ONLINE ===")
        println("1) Nueva Partida PVP (pendiente)")
        println("2) Nueva Partida PVE (pendiente)")
        println("3) Ver Records")
        println("4) Configuración")
        println("5) Salir")
        print("Opción: ")

        when (readLine()?.trim()) {
            "1" -> println("🚧 PVP aún no implementado")
            "2" -> println("🚧 PVE aún no implementado")
            "3" -> showRecords(records)
            "4" -> {
                settings = settingsMenu(settings)
                ClientSettingsStorage.save(settingsPath, settings)
                println("✅ Configuración guardada.")
            }
            "5" -> {
                println("Saliendo...")
                output.println("SALIR")
                input.readLine() // BYE si el server lo envía
                return
            }
            else -> println("Opción inválida")
        }
    }
}

private fun settingsMenu(current: ClientSettings): ClientSettings {
    var settings = current

    while (true) {
        println()
        println("=== CONFIGURACIÓN ===")
        println("1) Tamaño del tablero (actual: ${settings.boardSize})")
        println("2) Mejor de N partidas (actual: ${settings.bestOf})")
        println("3) Tiempo por turno en segundos (actual: ${settings.turnTimeSeconds})")
        println("4) Dificultad IA (actual: ${settings.difficulty})")
        println("5) Volver")
        print("Opción: ")

        when (readLine()?.trim()) {
            "1" -> {
                print("Nuevo tamaño (8-15): ")
                val v = readLine()?.toIntOrNull()
                if (v != null && v in 8..15) settings = settings.copy(boardSize = v)
                else println("Valor inválido.")
            }
            "2" -> {
                print("Elige 1, 3 o 5: ")
                val v = readLine()?.toIntOrNull()
                if (v != null && (v == 1 || v == 3 || v == 5)) settings = settings.copy(bestOf = v)
                else println("Valor inválido.")
            }
            "3" -> {
                print("Nuevo tiempo por turno (10-300): ")
                val v = readLine()?.toIntOrNull()
                if (v != null && v in 10..300) settings = settings.copy(turnTimeSeconds = v)
                else println("Valor inválido.")
            }
            "4" -> {
                print("Dificultad (EASY/NORMAL/HARD): ")
                val v = readLine()?.trim()?.uppercase()
                val d = runCatching { Difficulty.valueOf(v ?: "") }.getOrNull()
                if (d != null) settings = settings.copy(difficulty = d)
                else println("Valor inválido.")
            }
            "5" -> return settings
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
        val acc = stats.accuracy() * 100
        println("- $name | PVP: ${stats.pvpWins}W/${stats.pvpLosses}L | PVE: ${stats.pveWins}W/${stats.pveLosses}L | Best streak: ${stats.bestWinStreak} | Accuracy: ${"%.2f".format(acc)}%")
    }
}
