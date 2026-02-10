package com.ivan_martinez.client


import com.ivan_martinez.common.ConfigLoader
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

fun main() {
    val config = ConfigLoader.loadServerConfig()

    Socket(config.host, config.port).use { socket ->
        val input = BufferedReader(InputStreamReader(socket.getInputStream()))
        val output = PrintWriter(socket.getOutputStream(), true)
        val console = BufferedReader(InputStreamReader(System.`in`))

        println("Servidor: " + input.readLine()) // RECORDS:...
        println("Servidor: " + input.readLine()) // INFO:...

        while (true) {
            print("> ")
            val line = console.readLine() ?: break
            output.println(line)
            val resp = input.readLine() ?: break
            println("Servidor: $resp")
            if (resp == "BYE") break
        }
    }
}
