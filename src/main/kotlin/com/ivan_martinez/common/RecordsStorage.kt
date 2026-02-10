package com.ivan_martinez.common

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object RecordsStorage {

    // JSON “amigable” (bonito e ignora campos desconocidos)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Carga records desde un archivo. Si no existe o está vacío, crea records vacíos.
     */
    fun load(path: Path): Records {
        if (!Files.exists(path)) return Records()

        val content = Files.readString(path).trim()
        if (content.isEmpty()) return Records()


        return json.decodeFromString<Records>(content)
    }

    /**
     * Guarda de forma segura: escribe a un temporal y luego reemplaza (evita corruptos).
     */
    fun save(path: Path, records: Records) {
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")

        Files.writeString(tmp, json.encodeToString(records))
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
