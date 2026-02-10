package com.ivan_martinez.common


import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object ClientSettingsStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun load(path: Path): ClientSettings {
        if (!Files.exists(path)) return ClientSettings()

        val content = Files.readString(path).trim()
        if (content.isEmpty()) return ClientSettings()

        return json.decodeFromString(content)
    }

    fun save(path: Path, settings: ClientSettings) {
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(tmp, json.encodeToString(settings))
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
