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

    var playerName: String? = null
    var pvpSession: PvpSession? = null
    var pveSession: PveSession? = null

    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    socket.use { s ->
        val input = BufferedReader(InputStreamReader(s.getInputStream()))
        val output = PrintWriter(s.getOutputStream(), true)

        // Enviar records iniciales
        val records = RecordsStorage.load(recordsPath)
        output.println(Protocol.encode("RECORDS", json.encodeToString(records)))
        output.println(Protocol.encode("INFO", json.encodeToString(InfoMessage("Servidor listo"))))

        while (true) {
            val line = input.readLine() ?: break
            val (type, payload) = Protocol.decode(line)

            when (type) {

                // ================= HELLO =================
                "HELLO" -> {
                    val hello = json.decodeFromString<Hello>(payload)
                    val name = hello.name.trim()
                    playerName = if (name.isEmpty()) null else name
                    output.println(
                        Protocol.encode(
                            "INFO",
                            json.encodeToString(InfoMessage("Hola ${playerName ?: "Jugador"}"))
                        )
                    )
                }

                // ================= PVE =================
                "NEW_GAME_PVE" -> {
                    val req = json.decodeFromString<NewGamePveRequest>(payload)
                    val size = req.settings.boardSize

                    val board = if (req.placement != null) {
                        val err = PlacementValidator.validate(size, req.placement)
                        if (err != null) {
                            output.println(
                                Protocol.encode(
                                    "ERROR",
                                    json.encodeToString(ErrorMessage("Colocación inválida: $err"))
                                )
                            )
                            continue
                        }
                        BattleshipBoard.fromPlacement(size, req.placement)
                    } else {
                        BattleshipBoard.randomBoard(size)
                    }

                    pveSession = PveSession(size, board)

                    val started = pveSession!!.gameStartedPayload()
                    output.println(Protocol.encode("GAME_STARTED", json.encodeToString(started)))
                    output.println(Protocol.encode("TURN", json.encodeToString(TurnMessage(Attacker.PLAYER))))
                }

                "ATTACK" -> {
                    val session = pveSession
                    if (session == null) {
                        output.println(
                            Protocol.encode(
                                "ERROR",
                                json.encodeToString(ErrorMessage("No hay partida PVE activa"))
                            )
                        )
                        continue
                    }

                    val req = json.decodeFromString<AttackRequest>(payload)
                    val msgs = session.playerAttack(req.position)

                    for (m in msgs) {
                        when (m) {
                            is AttackResult ->
                                output.println(Protocol.encode("ATTACK_RESULT", json.encodeToString(m)))

                            is GameOver -> {
                                val name = playerName
                                if (name != null) {
                                    val snap = session.statsSnapshot()
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
                                pveSession = null
                            }

                            is TurnMessage ->
                                output.println(Protocol.encode("TURN", json.encodeToString(m)))

                            is ErrorMessage ->
                                output.println(Protocol.encode("ERROR", json.encodeToString(m)))

                            else ->
                                output.println(
                                    Protocol.encode(
                                        "ERROR",
                                        json.encodeToString(ErrorMessage("Mensaje desconocido"))
                                    )
                                )
                        }
                    }
                }

                // ================= GET RECORDS =================
                "GET_RECORDS" -> {
                    val rec = RecordsStorage.load(recordsPath)
                    output.println(Protocol.encode("RECORDS", json.encodeToString(rec)))
                }

                // ================= PVP =================
                "QUEUE_PVP" -> {
                    val name = playerName
                    if (name == null) {
                        output.println(
                            Protocol.encode(
                                "ERROR",
                                json.encodeToString(ErrorMessage("Primero envía HELLO"))
                            )
                        )
                        continue
                    }

                    val req = json.decodeFromString<QueuePvpRequest>(payload)

                    val sendMe: (String, String) -> Unit =
                        { t, p -> output.println(Protocol.encode(t, p)) }

                    val me = WaitingPvpPlayer(
                        name = name,
                        settings = req.settings,
                        send = sendMe,
                        attachSession = { s -> pvpSession = s }
                    )

                    val other = PvpQueue.enqueueOrMatch(me)

                    if (other == null) {
                        sendMe("INFO", json.encodeToString(InfoMessage("Esperando rival...")))
                    } else {

                        val size = req.settings.boardSize

                        val aBoard = BattleshipBoard.randomBoard(size)
                        val bBoard = BattleshipBoard.randomBoard(size)

                        val session = PvpSession(
                            size = size,
                            a = PvpSession.Side(other.name, other.send, aBoard, aBoard),
                            b = PvpSession.Side(name, sendMe, bBoard, bBoard)
                        )

                        other.attachSession(session)
                        pvpSession = session

                        other.send("INFO", json.encodeToString(InfoMessage("Rival encontrado: $name")))
                        sendMe("INFO", json.encodeToString(InfoMessage("Rival encontrado: ${other.name}")))

                        session.start(json)
                    }
                }

                "PVP_ATTACK" -> {
                    val sPvp = pvpSession
                    val name = playerName
                    if (sPvp == null || name == null) {
                        output.println(
                            Protocol.encode(
                                "ERROR",
                                json.encodeToString(ErrorMessage("No hay partida PVP activa"))
                            )
                        )
                        continue
                    }

                    val req = json.decodeFromString<AttackRequest>(payload)
                    val winner = sPvp.attack(name, req.position, json)

                    if (winner != null) {
                        pvpSession = null
                    }
                }

                // ================= SALIR =================
                "SALIR" -> {
                    output.println(
                        Protocol.encode(
                            "BYE",
                            json.encodeToString(InfoMessage("Hasta luego"))
                        )
                    )
                    playerName?.let { PvpQueue.cancelIfWaiting(it) }
                    break
                }

                else -> {
                    output.println(
                        Protocol.encode(
                            "ERROR",
                            json.encodeToString(ErrorMessage("Comando no reconocido: $type"))
                        )
                    )
                }
            }
        }
    }

    println("Cliente desconectado")
}
