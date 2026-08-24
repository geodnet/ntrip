package com.geodnet.ntrip.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.ArrayDeque

/**
 * Nordic UART Service (NUS) client: connects to a BLE GNSS/RTK receiver, parses its NMEA output
 * ($GGA/$RMC/$GST), and forwards RTCM correction bytes to it. All BLE calls assume the caller has
 * already obtained BLUETOOTH_CONNECT (API 31+) / BLUETOOTH+ACCESS_FINE_LOCATION (API <31) -- this
 * class does not request permissions itself.
 *
 * Untested against real hardware in this environment; the GATT callback flow and write chunking
 * follow the standard Android BLE patterns, but treat this as needing a real-device smoke test
 * before relying on it.
 */
@SuppressLint("MissingPermission")
class BleRtkReceiver(private val context: Context) {

    private val _state = MutableStateFlow(BleConnectionState())
    val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    private val _nmeaSentences = MutableSharedFlow<NmeaSentence>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val nmeaSentences: SharedFlow<NmeaSentence> = _nmeaSentences.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var negotiatedMtu: Int = DEFAULT_MTU
    private val lineBuffer = StringBuilder()
    private val pendingWrites = ArrayDeque<ByteArray>()
    private var writeInFlight = false

    fun connect(device: BluetoothDevice) {
        disconnect()
        _state.value = BleConnectionState(
            status = BleStatus.CONNECTING,
            deviceName = device.name,
            deviceAddress = device.address,
        )
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        gatt?.close()
        gatt = null
        rxCharacteristic = null
        negotiatedMtu = DEFAULT_MTU
        lineBuffer.clear()
        pendingWrites.clear()
        writeInFlight = false
        _state.value = BleConnectionState()
    }

    /** Queues RTCM bytes to send to the receiver, chunked to fit the negotiated MTU. Silently
     * dropped if not connected -- callers should check [state] before relying on delivery. */
    fun sendRtcm(data: ByteArray) {
        val characteristic = rxCharacteristic ?: return
        val chunkSize = (negotiatedMtu - ATT_WRITE_OVERHEAD).coerceAtLeast(20)
        var offset = 0
        while (offset < data.size) {
            val end = (offset + chunkSize).coerceAtMost(data.size)
            pendingWrites.addLast(data.copyOfRange(offset, end))
            offset = end
        }
        _state.update { it.copy(bytesToReceiver = it.bytesToReceiver + data.size) }
        pumpWriteQueue(characteristic)
    }

    private fun pumpWriteQueue(characteristic: BluetoothGattCharacteristic) {
        if (writeInFlight) return
        val next = pendingWrites.pollFirst() ?: return
        writeInFlight = true
        val g = gatt ?: run { writeInFlight = false; return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(characteristic, next, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            characteristic.value = next
            @Suppress("DEPRECATION")
            g.writeCharacteristic(characteristic)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.update { it.copy(status = BleStatus.DISCOVERING_SERVICES) }
                g.requestMtu(MTU_REQUEST)
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val errored = status != BluetoothGatt.GATT_SUCCESS
                _state.update {
                    it.copy(
                        status = if (errored) BleStatus.ERROR else BleStatus.DISCONNECTED,
                        errorMessage = if (errored) "GATT error $status" else null,
                    )
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(status = BleStatus.ERROR, errorMessage = "Service discovery failed") }
                return
            }
            val service = g.getService(BleUuids.NUS_SERVICE)
            val tx = service?.getCharacteristic(BleUuids.NUS_TX_CHARACTERISTIC)
            val rx = service?.getCharacteristic(BleUuids.NUS_RX_CHARACTERISTIC)
            if (service == null || tx == null || rx == null) {
                _state.update { it.copy(status = BleStatus.ERROR, errorMessage = "Nordic UART Service not found") }
                return
            }
            rxCharacteristic = rx
            g.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }
            }
            _state.update { it.copy(status = BleStatus.CONNECTED) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeInFlight = false
            rxCharacteristic?.let { pumpWriteQueue(it) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleIncoming(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                handleIncoming(characteristic.value ?: return)
            }
        }
    }

    private fun handleIncoming(bytes: ByteArray) {
        _state.update { it.copy(bytesFromReceiver = it.bytesFromReceiver + bytes.size) }
        lineBuffer.append(String(bytes, Charsets.US_ASCII))
        while (true) {
            val newlineIdx = lineBuffer.indexOf("\n")
            if (newlineIdx < 0) {
                if (lineBuffer.length > MAX_LINE_BUFFER) lineBuffer.clear() // guard against a runaway buffer
                break
            }
            val line = lineBuffer.substring(0, newlineIdx)
            lineBuffer.delete(0, newlineIdx + 1)
            val sentence = NmeaParser.parse(line) ?: continue
            _nmeaSentences.tryEmit(sentence)
            when (sentence) {
                is NmeaSentence.Gga -> _state.update { it.copy(latestFix = sentence) }
                is NmeaSentence.Gst -> _state.update { it.copy(latestGst = sentence) }
                else -> Unit
            }
        }
    }

    companion object {
        private const val DEFAULT_MTU = 23 // BLE default before any negotiation
        private const val MTU_REQUEST = 185 // leaves headroom under the common 247-byte ceiling
        private const val ATT_WRITE_OVERHEAD = 3
        private const val MAX_LINE_BUFFER = 4096
    }
}
