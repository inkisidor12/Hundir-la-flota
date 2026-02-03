package com.ivan_martinez

import com.ivan_martinez.common.ConfigLoader
import com.ivan_martinez.common.RecordsStorage
import java.nio.file.Paths

fun main() {
    val config = ConfigLoader.loadServerConfig()
    println("CONFIG -> host=${config.host}, port=${config.port}, maxClients=${config.maxClients}")

    // OJO: src/main/resources NO es escribible cuando generas jar.
    // Para desarrollo, guarda records en una carpeta "data".
    val dataDir = Paths.get("data")
    dataDir.toFile().mkdirs()
    val recordsPath = dataDir.resolve("records.json")

    val records = RecordsStorage.load(recordsPath)
    println("RECORDS -> players=${records.players.size}")

    RecordsStorage.save(recordsPath, records)
    println("Records guardados en: $recordsPath")
}
