package com.ivan_martinez.common


import kotlinx.serialization.Serializable

@Serializable
data class NewGamePveRequest(
    val settings: ClientSettings,
    val placement: PlacementConfig? = null
)