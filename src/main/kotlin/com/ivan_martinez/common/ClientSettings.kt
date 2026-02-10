package com.ivan_martinez.common


import kotlinx.serialization.Serializable

//esto lo que hace es crea lo de los turno yç
// la dificultad de la ia
//esto es para el PVE
@Serializable
data class ClientSettings(
    val boardSize: Int = 10,
    val bestOf: Int = 3,
    val turnTimeSeconds: Int = 60,
    val difficulty: Difficulty = Difficulty.NORMAL
)

@Serializable
enum class Difficulty { EASY, NORMAL, HARD }
