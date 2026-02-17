package com.ivan_martinez.server

import com.ivan_martinez.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import java.nio.file.Paths

fun main() = runBlocking {
    val config = ConfigLoader.loadServerConfig()

    val dataDir = Paths.get("data")
    dataDir.toFile().mkdirs()
    val recordsPath = dataDir.resolve("records.json")

    // Carga records al iniciar (si no existe, queda vacío)
    RecordsStorage.load(recordsPath)

    val serverSocket = ServerSocket(config.port)
    println("Servidor escuchando en ${config.host}:${config.port} (maxClients=${config.maxClients})")

    while (true) {
        val client = serverSocket.accept()
        println("Cliente conectado: ${client.inetAddress.hostAddress}:${client.port}")

        launch(Dispatchers.IO) {
            handleClient(client, recordsPath)
        }
    }
}

private fun handleClient(socket: Socket, recordsPath: Path) {
    // nombre del cliente
    var playerName: String? = null

    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    socket.use { s ->
        val input = BufferedReader(InputStreamReader(s.getInputStream()))
        val output = PrintWriter(s.getOutputStream(), true)

        // 1) Enviar records al conectarse (requisito) usando el protocolo
        val records = RecordsStorage.load(recordsPath)
        output.println(Protocol.encode("RECORDS", json.encodeToString(records)))

        // 2) Mensaje info (también por protocolo, para que el cliente lo pueda parsear si quiere)
        output.println(Protocol.encode("INFO", json.encodeToString(InfoMessage("Servidor listo"))))

        // Sesión PVE (una por cliente)
        var session: PveSession? = null

        while (true) {
            val line = input.readLine() ?: break
            val (type, payload) = Protocol.decode(line)

            when (type) {
                "HELLO" -> {
                    val hello = json.decodeFromString<Hello>(payload)
                    val name = hello.name.trim()
                    playerName = if (name.isEmpty()) null else name
                    output.println(Protocol.encode("INFO", json.encodeToString(InfoMessage("Hola ${playerName ?: "Jugador"}"))))
                }

                "NEW_GAME_PVE" -> {
                    val req = json.decodeFromString<NewGamePveRequest>(payload)
                    val size = req.settings.boardSize // configurable (10 por defecto)
                    session = PveSession(size)

                    val started = session!!.gameStartedPayload()
                    output.println(Protocol.encode("GAME_STARTED", json.encodeToString(started)))
                    output.println(Protocol.encode("TURN", json.encodeToString(TurnMessage(Attacker.PLAYER))))
                }

                "ATTACK" -> {
                    val sSession = session
                    if (sSession == null) {
                        output.println(Protocol.encode("ERROR", json.encodeToString(ErrorMessage("No hay partida activa"))))
                        continue
                    }

                    val req = json.decodeFromString<AttackRequest>(payload)
                    val msgs = sSession.playerAttack(req.position)

                    for (m in msgs) {
                        when (m) {
                            is AttackResult -> output.println(Protocol.encode("ATTACK_RESULT", json.encodeToString(m)))

                            is GameOver -> {
                                val name = playerName

                                if (name != null) {
                                    val snap = sSession.statsSnapshot()
                                    val won = (m.winner == Attacker.PLAYER)

                                    val rec = RecordsStorage.load(recordsPath)
                                    val ps = RecordsUpdater.ensurePlayer(rec, name)

                                    RecordsUpdater.applyPveResult(
                                        stats = ps,
                                        won = won,
                                        shots = snap.playerShots,
                                        hits = snap.playerHits,
                                        turnsToWin = if (won) snap.playerTurns else null
                                    )

                                    RecordsStorage.save(recordsPath, rec)
                                }

                                output.println(Protocol.encode("GAME_OVER", json.encodeToString(m)))
                            }

                            is TurnMessage -> output.println(Protocol.encode("TURN", json.encodeToString(m)))
                            is ErrorMessage -> output.println(Protocol.encode("ERROR", json.encodeToString(m)))
                            else -> output.println(Protocol.encode("ERROR", json.encodeToString(ErrorMessage("Mensaje desconocido"))))
                        }
                    }
                }
                "GET_RECORDS" -> {
                    // Devuelve records actualizados al cliente (refresco tras una partida)
                    val rec = RecordsStorage.load(recordsPath)
                    output.println(Protocol.encode("RECORDS", json.encodeToString(rec)))
                }

                "SALIR" -> {
                    output.println(Protocol.encode("BYE", json.encodeToString(InfoMessage("Hasta luego"))))
                    break
                }

                else -> {
                    output.println(Protocol.encode("ERROR", json.encodeToString(ErrorMessage("Comando no reconocido: $type"))))
                }
            }
        }
    }

    println("Cliente desconectado")
}
