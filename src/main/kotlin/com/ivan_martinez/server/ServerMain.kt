package com.ivan_martinez.server


import com.ivan_martinez.common.ConfigLoader
import com.ivan_martinez.common.RecordsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Paths

fun main() = runBlocking {
    val config = ConfigLoader.loadServerConfig()
    val dataDir = Paths.get("data")
    dataDir.toFile().mkdirs()
    val recordsPath = dataDir.resolve("records.json")

    // Carga records al iniciar (si no existe, queda vacío)
    val records = RecordsStorage.load(recordsPath)

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

private fun handleClient(socket: Socket, recordsPath: java.nio.file.Path) {
    socket.use { s ->
        val input = BufferedReader(InputStreamReader(s.getInputStream()))
        val output = PrintWriter(s.getOutputStream(), true)

        // 1) Enviar records al conectarse (requisito)
        val records = RecordsStorage.load(recordsPath)
        val recordsJson = kotlinx.serialization.json.Json.encodeToString(
            com.ivan_martinez.common.Records.serializer(),
            records
        )
        output.println("RECORDS:$recordsJson")

        // 2) Bucle simple: eco/diagnóstico (por ahora)
        output.println("INFO: Servidor listo. Escribe PING o SALIR")

        while (true) {
            val line = input.readLine() ?: break
            when (line.trim().uppercase()) {
                "PING" -> output.println("PONG")
                "SALIR" -> {
                    output.println("BYE")
                    break
                }
                else -> output.println("ERROR: Comando no reconocido")
            }
        }
    }

    println("Cliente desconectado")
}
