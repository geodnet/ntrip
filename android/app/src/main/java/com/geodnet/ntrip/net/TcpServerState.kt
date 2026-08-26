package com.geodnet.ntrip.net

data class TcpServerState(
    val port: Int,
    val listening: Boolean = false,
    val clientCount: Int = 0,
    val bytesSent: Long = 0,
    val errorMessage: String? = null,
)
