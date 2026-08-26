package com.geodnet.ntrip.ble

enum class BleStatus { DISCONNECTED, CONNECTING, DISCOVERING_SERVICES, CONNECTED, ERROR }

data class BleConnectionState(
    val status: BleStatus = BleStatus.DISCONNECTED,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val errorMessage: String? = null,
    val latestFix: NmeaSentence.Gga? = null,
    val latestGst: NmeaSentence.Gst? = null,
    val latestGsa: NmeaSentence.Gsa? = null,
    val bytesFromReceiver: Long = 0,
    val bytesToReceiver: Long = 0,
    val messagesReceived: Long = 0,
    val messagesSent: Long = 0,
    val nmeaCounts: Map<String, Int> = emptyMap(),
    val rtcmCounts: Map<String, Int> = emptyMap(),
    val mtu: Int = 23,
)

/** One scan result, kept minimal -- just enough to show a picker and connect. */
data class BleDeviceInfo(
    val name: String?,
    val address: String,
    val rssi: Int,
)
