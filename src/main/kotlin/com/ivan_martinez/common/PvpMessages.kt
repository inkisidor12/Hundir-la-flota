package com.ivan_martinez.common


import kotlinx.serialization.Serializable

@Serializable
data class QueuePvpRequest(val settings: ClientSettings, val placementConfig: PlacementConfig? =null )

@Serializable
data class MatchFound(val opponentName: String, val boardSize: Int, val myShips: List<ShipPlacement>)

@Serializable
data class PvpTurn(val who: PvpWho)

@Serializable
enum class PvpWho { YOU, OPPONENT }