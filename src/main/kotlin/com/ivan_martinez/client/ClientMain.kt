package com.ivan_martinez.client

import com.ivan_martinez.common.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.nio.file.Path
import java.nio.file.Paths

private val json = Json { ignoreUnknownKeys = true; }

// Wrapper para poder “actualizar” records desde funciones sin tener que rehacer toda la arquitectura
private data class RecordsRef(var value: Records)

fun main() {
    val config = ConfigLoader.loadServerConfig()

    // settings se cargan ANTES del menú y se pasan al menú
    val dataDir = Paths.get("data")
    dataDir.toFile().mkdirs()
    val settingsPath = dataDir.resolve("client-settings.json")
    var settings = ClientSettingsStorage.load(settingsPath)

    Socket(config.host, config.port).use { socket ->
        val input = BufferedReader(InputStreamReader(socket.getInputStream()))
        val output = PrintWriter(socket.getOutputStream(), true)

        // 1) Esperamos el primer mensaje RECORDS del servidor
        val firstLine = input.readLine() ?: error("Servidor cerró la conexión")
        val (t1, p1) = Protocol.decode(firstLine)
        require(t1 == "RECORDS") { "Se esperaba RECORDS y llegó $t1" }

        // records ahora es “mutable” vía RecordsRef (para poder refrescar tras partidas)
        val recordsRef = RecordsRef(json.decodeFromString<Records>(p1))
        println("✅ Records sincronizados. Jugadores: ${recordsRef.value.players.size}")

        // Saluda al cliente
        print("Nombre de jugador: ")
        val name = readLine()?.trim().orEmpty()
        output.println(Protocol.encode("HELLO", json.encodeToString(Hello(name))))

        // 2) Segundo mensaje (INFO) si llega, lo leemos pero no es obligatorio para el menú
        // (Si el server manda algo que no sea INFO, lo mostramos y seguimos)
        val secondLine = input.readLine()
        if (secondLine != null) {
            val decoded = runCatching { Protocol.decode(secondLine) }.getOrNull()
            if (decoded != null) {
                val (t2, p2) = decoded
                if (t2 == "INFO") {
                    val info = runCatching { json.decodeFromString<InfoMessage>(p2) }.getOrNull()
                    if (info != null) println("ℹ️ ${info.message}")
                } else {
                    println("ℹ️ Mensaje inicial extra: $t2")
                }
            } else {
                println("ℹ️ Mensaje inicial extra: $secondLine")
            }
        }

        println("⚙️ Config actual: board=${settings.boardSize}, bestOf=${settings.bestOf}, turn=${settings.turnTimeSeconds}s, diff=${settings.difficulty}")

        // 3) Menú
        menuLoop(recordsRef, output, input, settingsPath, settings)
    }
}

private fun menuLoop(
    recordsRef: RecordsRef,
    output: PrintWriter,
    input: BufferedReader,
    settingsPath: Path,
    initialSettings: ClientSettings
) {
    var settings = initialSettings

    while (true) {
        println()
        println("=== HUNDIR LA FLOTA ONLINE ===")
        println("1) Nueva Partida PVP")
        println("2) Nueva Partida PVE")
        println("3) Ver Records")
        println("4) Configuración")
        println("5) Salir")
        println("6) Colocación manual de barcos (guardar)")
        println("7) Borrar colocación guardada")
        print("Opción: ")

        when (readLine()?.trim()) {
            "1" -> {
                startPvp(output, input, settings)
                // Tras terminar PVP, refrescamos records del servidor para que “Ver Records” muestre lo último
                refreshRecords(recordsRef, output, input)
            }
            "2" -> {
                startPve(output, input, settings)
                // Tras terminar PVE, refrescamos records del servidor para que “Ver Records” muestre lo último
                refreshRecords(recordsRef, output, input)
            }
            "3" -> showRecords(recordsRef.value)
            "4" -> {
                settings = settingsMenu(settings)
                ClientSettingsStorage.save(settingsPath, settings)
                println("✅ Configuración guardada.")
            }
            "5" -> {
                println("Saliendo...")
                output.println(Protocol.encode("SALIR", json.encodeToString(InfoMessage("bye"))))
                input.readLine() // BYE...
                return
            }
            "6" -> {
                val placement = readManualPlacement(settings.boardSize)
                if (placement != null) {
                    val err = PlacementValidator.validate(settings.boardSize, placement)
                    if (err != null) {
                        println("❌ Colocación inválida: $err")
                    } else {
                        settings = settings.copy(placement = placement)
                        println("✅ Colocación guardada en configuración.")
                    }
                }
            }
            "7" -> {
                settings = settings.copy(placement = null)
                println("✅ Colocación eliminada.")
            }
            else -> println("Opción inválida")
        }
    }
}

private fun refreshRecords(recordsRef: RecordsRef, output: PrintWriter, input: BufferedReader) {
    // Pedimos records actualizados al servidor
    output.println(Protocol.encode("GET_RECORDS", json.encodeToString(InfoMessage("get"))))

    // Esperamos RECORDS (saltando INFO si aparece)
    while (true) {
        val line = input.readLine() ?: return
        val (t, p) = Protocol.decode(line)

        when (t) {
            "RECORDS" -> {
                recordsRef.value = json.decodeFromString<Records>(p)
                println("✅ Records actualizados. Jugadores: ${recordsRef.value.players.size}")
                return
            }
            "INFO" -> {
                // ignorar
            }
            "ERROR" -> {
                val err = runCatching { json.decodeFromString<ErrorMessage>(p) }.getOrNull()
                println("❌ No se pudieron actualizar records: ${err?.message ?: p}")
                return
            }
            else -> {
                // ignoramos cualquier otra cosa
            }
        }
    }
}
private fun readManualPlacement(boardSize: Int): PlacementConfig? {
    println()
    println("=== COLOCACIÓN MANUAL ===")
    println("Formato: TIPO=POS,POS,POS...")
    println("Ej: CARRIER=A1,A2,A3,A4,A5")
    println("Tipos: CARRIER, BATTLESHIP, CRUISER, DESTROYER")
    println("Tienes que introducir: 1xCARRIER, 2xBATTLESHIP, 3xCRUISER, 4xDESTROYER")
    println("Escribe 'fin' para terminar o 'cancelar' para salir.")
    println()

    val ships = mutableListOf<ShipPlacement>()

    while (true) {
        print("> ")
        val line = readLine()?.trim().orEmpty()
        if (line.equals("cancelar", true)) return null
        if (line.equals("fin", true)) break
        if (line.isEmpty()) continue

        val parts = line.split("=")
        if (parts.size != 2) {
            println("❌ Formato inválido. Usa TIPO=POS,POS,...")
            continue
        }

        val typeStr = parts[0].trim().uppercase()
        val positionsStr = parts[1].trim()

        val type = runCatching { ShipType.valueOf(typeStr) }.getOrNull()
        if (type == null) {
            println("❌ Tipo inválido: $typeStr")
            continue
        }

        val positions = positionsStr.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        if (positions.isEmpty()) {
            println("❌ Debes indicar posiciones.")
            continue
        }

        // Validación básica: dentro del tablero (para dar feedback rápido)
        val ok = positions.all { PositionCodec.parse(it, boardSize) != null }
        if (!ok) {
            println("❌ Alguna posición está fuera del tablero $boardSize x $boardSize.")
            continue
        }

        ships.add(ShipPlacement(type, positions))
        println("✅ Añadido: $type -> $positions")
    }

    return PlacementConfig(ships)
}

private fun startPvp(output: PrintWriter, input: BufferedReader, settings: ClientSettings) {
    println()
    println("=== PVP: BUSCANDO RIVAL ===")

    // 1) Entramos en la cola PVP (matchmaking simple)
    val req = QueuePvpRequest(settings, settings.placement)
    output.println(Protocol.encode("QUEUE_PVP", json.encodeToString(req)))

    // 2) Esperamos PVP_MATCH_FOUND (saltando INFO/ERROR)
    var match: MatchFound? = null
    while (match == null) {
        val line = input.readLine() ?: run {
            println("Servidor desconectado.")
            return
        }
        val (t, p) = Protocol.decode(line)

        when (t) {
            "PVP_MATCH_FOUND" -> match = json.decodeFromString<MatchFound>(p)

            "INFO" -> {
                val info = runCatching { json.decodeFromString<InfoMessage>(p) }.getOrNull()
                if (info != null) println("ℹ️ ${info.message}")
            }

            "ERROR" -> {
                val err = runCatching { json.decodeFromString<ErrorMessage>(p) }.getOrNull()
                println("❌ Error: ${err?.message ?: p}")
                return
            }

            else -> {
                // ignoramos cualquier otra cosa mientras esperamos rival
                println("⚠️ Ignorando mensaje: $t")
            }
        }
    }

    val size = match.boardSize
    val myBoard = ClientBoard(size)
    val enemyRadar = Radar(size)

    // Colocación (servidor nos manda nuestros barcos auto-colocados)
    for (ship in match.myShips) {
        myBoard.placeShip(ship.positions)
    }

    println("✅ Rival encontrado: ${match.opponentName}")
    println("✅ Empieza la partida PVP.")

    // 3) Bucle de juego PVP: esperamos turnos y resultados
    while (true) {
        val line = input.readLine() ?: run {
            println("Servidor desconectado.")
            return
        }
        val (t, p) = Protocol.decode(line)

        when (t) {
            "PVP_TURN" -> {
                val turn = json.decodeFromString<PvpTurn>(p)

                if (turn.who == PvpWho.YOU) {
                    // Mi turno: render + pedir ataque
                    println()
                    renderBoards(myBoard, enemyRadar)

                    print("Tu ataque (ej: A1, C4) o 'salir': ")
                    val pos = readLine()?.trim() ?: return
                    if (pos.equals("salir", ignoreCase = true)) return

                    output.println(Protocol.encode("PVP_ATTACK", json.encodeToString(AttackRequest(pos))))
                } else {
                    // Turno del rival: no hacemos nada, solo esperamos resultados
                    println("⏳ Turno del rival...")
                }
            }

            "PVP_ATTACK_RESULT_YOU" -> {
                // Resultado de MI disparo
                val res = json.decodeFromString<AttackResult>(p)
                enemyRadar.mark(res.position, res.result)
                println("🎯 Tú: ${res.position} -> ${res.result}${if (res.sunk) " (HUNDIDO ${res.sunkShip})" else ""}")
            }

            "PVP_ATTACK_RESULT_OPP" -> {
                // Resultado del disparo DEL RIVAL
                val res = json.decodeFromString<AttackResult>(p)
                myBoard.markShot(res.position, res.result)
                println("💥 Rival: ${res.position} -> ${res.result}${if (res.sunk) " (HUNDIDO ${res.sunkShip})" else ""}")
            }

            "PVP_GAME_OVER" -> {
                val over = json.decodeFromString<GameOver>(p)
                println()
                println("🏁 PVP GAME OVER -> ganador: ${over.winner} (${over.reason})")
                renderBoards(myBoard, enemyRadar)
                return
            }

            "INFO" -> {
                val info = runCatching { json.decodeFromString<InfoMessage>(p) }.getOrNull()
                if (info != null) println("ℹ️ ${info.message}")
            }

            "ERROR" -> {
                val err = runCatching { json.decodeFromString<ErrorMessage>(p) }.getOrNull()
                println("❌ Error: ${err?.message ?: p}")
            }

            else -> {
                // Mensaje desconocido: lo mostramos por si acaso
                println("⚠️ Mensaje desconocido: $t -> $p")
            }
        }
    }
}

private fun startPve(output: PrintWriter, input: BufferedReader, settings: ClientSettings) {
    println()
    println("=== PVE: NUEVA PARTIDA ===")

    // 1) Pedimos al servidor crear partida PVE
    val req = NewGamePveRequest(settings, settings.placement)
    output.println(Protocol.encode("NEW_GAME_PVE", json.encodeToString(req)))

    // 2) Esperamos GAME_STARTED (saltando INFO u otros mensajes)
    var started: GameStarted? = null

    while (started == null) {
        val line = input.readLine() ?: run {
            println("Servidor desconectado.")
            return
        }

        val (type, payload) = Protocol.decode(line)

        when (type) {
            "GAME_STARTED" -> started = json.decodeFromString<GameStarted>(payload)

            "ERROR" -> {
                val err = runCatching { json.decodeFromString<ErrorMessage>(payload) }.getOrNull()
                println("❌ Error: ${err?.message ?: payload}")
                return
            }

            "INFO" -> {
                val info = runCatching { json.decodeFromString<InfoMessage>(payload) }.getOrNull()
                if (info != null) println("ℹ️ ${info.message}")
            }

            // si llega TURN antes (no debería, pero por seguridad)
            "TURN" -> {
                // lo ignoramos aquí; luego ya leeremos el turno
            }

            else -> {
                // ignoramos mensajes desconocidos mientras esperamos GAME_STARTED
                println("⚠️ Ignorando mensaje: $type")
            }
        }
    }

    val size = started.boardSize

    // 3) Construimos estado local para render
    val myBoard = ClientBoard(size)
    val enemyRadar = Radar(size)

    // Colocación (servidor nos manda nuestros barcos)
    for (ship in started.myShips) {
        myBoard.placeShip(ship.positions)
    }

    // 4) Esperamos TURN inicial (PLAYER)
    val turnLine = input.readLine() ?: run {
        println("Servidor desconectado.")
        return
    }
    val (tTurn, pTurn) = Protocol.decode(turnLine)
    if (tTurn == "TURN") {
        val turn = json.decodeFromString<TurnMessage>(pTurn)
        if (turn.who != Attacker.PLAYER) {
            println("⚠️ Turno inesperado: ${turn.who}")
        }
    }

    // 5) Bucle de juego
    while (true) {
        println()
        renderBoards(myBoard, enemyRadar)

        print("Tu ataque (ej: A1, C4) o 'salir': ")
        val pos = readLine()?.trim() ?: return
        if (pos.equals("salir", ignoreCase = true)) return

        output.println(Protocol.encode("ATTACK", json.encodeToString(AttackRequest(pos))))

        // Leemos mensajes hasta que vuelva a ser nuestro turno o acabe la partida
        while (true) {
            val msgLine = input.readLine() ?: run {
                println("Servidor desconectado.")
                return
            }
            val (mt, mp) = Protocol.decode(msgLine)

            when (mt) {
                "ATTACK_RESULT" -> {
                    val res = json.decodeFromString<AttackResult>(mp)
                    if (res.by == Attacker.PLAYER) {
                        // afecta a radar enemigo
                        enemyRadar.mark(res.position, res.result)
                        println("🎯 Tú: ${res.position} -> ${res.result}${if (res.sunk) " (HUNDIDO ${res.sunkShip})" else ""}")
                    } else {
                        // afecta a mi tablero
                        myBoard.markShot(res.position, res.result)
                        println("💥 IA: ${res.position} -> ${res.result}${if (res.sunk) " (HUNDIDO ${res.sunkShip})" else ""}")
                    }
                }

                "TURN" -> {
                    val turn = json.decodeFromString<TurnMessage>(mp)
                    if (turn.who == Attacker.PLAYER) {
                        break // volvemos a pedir coordenada
                    }
                }

                "GAME_OVER" -> {
                    val over = json.decodeFromString<GameOver>(mp)
                    println()
                    println("🏁 GAME OVER -> ganador: ${over.winner} (${over.reason})")
                    renderBoards(myBoard, enemyRadar)
                    return
                }

                "ERROR" -> {
                    val err = runCatching { json.decodeFromString<ErrorMessage>(mp) }.getOrNull()
                    println("❌ Error: ${err?.message ?: mp}")
                    // en caso de error, volvemos a pedir ataque
                    break
                }

                "INFO" -> {
                    val info = runCatching { json.decodeFromString<InfoMessage>(mp) }.getOrNull()
                    if (info != null) println("ℹ️ ${info.message}")
                }

                else -> {
                    // Mensaje desconocido: lo mostramos por si acaso
                    println("⚠️ Mensaje desconocido: $mt -> $mp")
                }
            }
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

/**
 * Tablero del jugador (se ve con barcos) + impactos recibidos.
 */
private class ClientBoard(private val size: Int) {
    private val ships = Array(size) { CharArray(size) { '.' } } // '.' agua, 'S' barco
    private val shots = Array(size) { CharArray(size) { ' ' } } // 'H' hit, 'M' miss, ' ' nada

    fun placeShip(positions: List<String>) {
        for (p in positions) {
            val rc = parsePos(p) ?: continue
            ships[rc.first][rc.second] = 'S'
        }
    }

    fun markShot(position: String, result: ShotResult) {
        val rc = parsePos(position) ?: return
        shots[rc.first][rc.second] = if (result == ShotResult.HIT) 'H' else 'M'
    }

    fun renderLines(): List<String> {
        val lines = mutableListOf<String>()
        lines.add(headerLine())
        for (r in 0 until size) {
            val rowNum = (r + 1).toString().padStart(2, ' ')
            val sb = StringBuilder()
            sb.append(rowNum).append(" ")
            for (c in 0 until size) {
                val ch = when (shots[r][c]) {
                    'H' -> 'X'
                    'M' -> 'o'
                    else -> ships[r][c] // 'S' o '.'
                }
                sb.append(ch).append(' ')
            }
            lines.add(sb.toString())
        }
        return lines
    }

    private fun headerLine(): String {
        val sb = StringBuilder("   ")
        for (c in 0 until size) sb.append(('A'.code + c).toChar()).append(' ')
        return sb.toString()
    }

    private fun parsePos(pos: String): Pair<Int, Int>? {
        val p = pos.trim().uppercase()
        if (p.length < 2) return null
        val col = p[0] - 'A'
        val row = p.substring(1).toIntOrNull()?.minus(1) ?: return null
        if (row !in 0 until size || col !in 0 until size) return null
        return row to col
    }
}

/**
 * Radar enemigo: solo sabemos si hemos acertado (H) o fallado (M).
 */
private class Radar(private val size: Int) {
    private val grid = Array(size) { CharArray(size) { ' ' } } // 'H', 'M', ' '

    fun mark(position: String, result: ShotResult) {
        val rc = parsePos(position) ?: return
        grid[rc.first][rc.second] = if (result == ShotResult.HIT) 'H' else 'M'
    }

    fun renderLines(): List<String> {
        val lines = mutableListOf<String>()
        lines.add(headerLine())
        for (r in 0 until size) {
            val rowNum = (r + 1).toString().padStart(2, ' ')
            val sb = StringBuilder()
            sb.append(rowNum).append(" ")
            for (c in 0 until size) {
                val ch = when (grid[r][c]) {
                    'H' -> 'X'
                    'M' -> 'o'
                    else -> '.'
                }
                sb.append(ch).append(' ')
            }
            lines.add(sb.toString())
        }
        return lines
    }

    private fun headerLine(): String {
        val sb = StringBuilder("   ")
        for (c in 0 until size) sb.append(('A'.code + c).toChar()).append(' ')
        return sb.toString()
    }

    private fun parsePos(pos: String): Pair<Int, Int>? {
        val p = pos.trim().uppercase()
        if (p.length < 2) return null
        val col = p[0] - 'A'
        val row = p.substring(1).toIntOrNull()?.minus(1) ?: return null
        if (row !in 0 until size || col !in 0 until size) return null
        return row to col
    }
}

private fun renderBoards(my: ClientBoard, radar: Radar) {
    val left = my.renderLines()
    val right = radar.renderLines()
    val titleLeft = "MI FLOTA"
    val titleRight = "RADAR ENEMIGO"
    println(titleLeft.padEnd(28) + "   " + titleRight)
    for (i in left.indices) {
        val l = left[i].padEnd(28)
        val r = right.getOrNull(i) ?: ""
        println("$l   $r")
    }
}