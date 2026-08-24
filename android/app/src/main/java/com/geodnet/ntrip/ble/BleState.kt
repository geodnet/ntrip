package com.geodnet.ntrip.ble

enum class BleStatus { DISCONNECTED, CONNECTING, DISCOVERING_SERVICES, CONNECTED, ERROR }

data class BleConnectionState(
    val status: BleStatus = BleStatus.DISCONNECTED,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val errorMessage: String? = null,
    val latestFix: NmeaSentence.Gga? = null,
    val latestGst: NmeaSentence.Gst? = null,
    val bytesFromReceiver: Long = 0,
    val bytesToReceiver: Long = 0,
)

/** One scan result, kept minimal -- just enough to show a picker and connect. */
data class BleDeviceInfo(
    val name: String?,
    val address: String,
    val rssi: Int,
)
