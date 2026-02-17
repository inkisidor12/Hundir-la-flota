package com.ivan_martinez.common


import kotlinx.serialization.Serializable

@Serializable
data class InfoMessage(val message: String)

@Serializable
data class ErrorMessage(val message: String)

@Serializable
data class Hello(val name: String)