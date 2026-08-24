package com.geodnet.ntrip.ble

import java.util.UUID

/** Nordic UART Service (NUS) UUIDs -- the de facto standard most BLE GNSS/RTK receivers use for
 * a simple serial-over-BLE bridge (NMEA out, RTCM in). */
object BleUuids {
    val NUS_SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

    /** Receiver -> phone (notify): NMEA sentences. */
    val NUS_TX_CHARACTERISTIC: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    /** Phone -> receiver (write): RTCM correction data. */
    val NUS_RX_CHARACTERISTIC: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    /** Standard Client Characteristic Configuration Descriptor, used to enable notifications. */
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}
