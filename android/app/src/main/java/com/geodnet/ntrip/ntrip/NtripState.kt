package com.geodnet.ntrip.ntrip

enum class NtripStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class NtripState(
    val status: NtripStatus = NtripStatus.DISCONNECTED,
    val bytesReceived: Long = 0L,
    val errorMessage: String? = null,
)
