package com.ivan_martinez.common


import java.util.Properties

object ConfigLoader {

    /**
     * Lee server.properties desde src/main/resources
     */
    fun loadServerConfig(resourceName: String = "server.properties"): ServerConfig {
        val props = Properties()

        val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourceName)
            ?: throw IllegalStateException("No se encuentra $resourceName en src/main/resources")

        inputStream.use { props.load(it) }

        val host = props.getProperty("server.host") ?: "localhost"
        val port = props.getProperty("server.port")?.toIntOrNull()
            ?: throw IllegalArgumentException("server.port inválido en $resourceName")
        val maxClients = props.getProperty("max.clients")?.toIntOrNull()
            ?: throw IllegalArgumentException("max.clients inválido en $resourceName")

        return ServerConfig(host, port, maxClients)
    }
}
