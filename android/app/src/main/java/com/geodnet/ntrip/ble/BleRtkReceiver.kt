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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/**
 * Nordic UART Service (NUS) client: connects to a BLE GNSS/RTK receiver, parses its NMEA output
 * ($GGA/$RMC/$GST), and forwards RTCM correction bytes to it. All BLE calls assume the caller has
 * already obtained BLUETOOTH_CONNECT (API 31+) / BLUETOOTH+ACCESS_FINE_LOCATION (API <31) -- this
 * class does not request permissions itself.
 *
 * Auto-reconnects to the last-connected device (exponential backoff, 2s..30s, retried
 * indefinitely) on any GATT disconnect that wasn't requested via [disconnect] -- a dropped
 * connection out in the field should recover on its own, not require the user to notice and
 * re-pick the device from the scan list.
 *
 * Untested against real hardware in this environment; the GATT callback flow and write chunking
 * follow the standard Android BLE patterns, but treat this as needing a real-device smoke test
 * before relying on it.
 */
@SuppressLint("MissingPermission")
class BleRtkReceiver(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(BleConnectionState())
    val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    private val _nmeaSentences = MutableSharedFlow<NmeaSentence>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val nmeaSentences: SharedFlow<NmeaSentence> = _nmeaSentences.asSharedFlow()

    /** Every raw NMEA line as received, regardless of whether [NmeaParser] recognizes it -- for
     * verbatim forwarding to the NMEA TCP server (see LocationFixAggregator), which shouldn't be
     * limited to the sentence types this app itself parses. */
    private val _rawLines = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val rawLines: SharedFlow<String> = _rawLines.asSharedFlow()

    /** Every raw incoming byte chunk, before line-splitting -- for the "Raw Binary Stream Logger"
     * (readme.md section 6.1), which wants the exact bytes as received, not reconstructed text. */
    private val _rawBytes = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val rawBytes: SharedFlow<ByteArray> = _rawBytes.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var negotiatedMtu: Int = DEFAULT_MTU
    private val lineBuffer = StringBuilder()
    private val pendingWrites = ArrayDeque<ByteArray>()
    private var writeInFlight = false

    private var lastDevice: BluetoothDevice? = null
    private var userInitiatedDisconnect = false
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    fun connect(device: BluetoothDevice) {
        userInitiatedDisconnect = false
        lastDevice = device
        reconnectJob?.cancel()
        reconnectAttempt = 0
        teardownGatt()
        _state.value = BleConnectionState(
            status = BleStatus.CONNECTING,
            deviceName = device.name,
            deviceAddress = device.address,
        )
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        userInitiatedDisconnect = true
        lastDevice = null
        reconnectJob?.cancel()
        teardownGatt()
        _state.value = BleConnectionState()
    }

    /** Releases this instance's coroutine scope -- call once when the owner (NtripViewModel) is
     * itself being torn down, in addition to [disconnect]. Not calling this just leaks a
     * SupervisorJob for the process lifetime, not a crash, but there's no reason not to. */
    fun dispose() {
        reconnectJob?.cancel()
        scope.cancel()
    }

    private fun teardownGatt() {
        gatt?.close()
        gatt = null
        rxCharacteristic = null
        negotiatedMtu = DEFAULT_MTU
        lineBuffer.clear()
        pendingWrites.clear()
        writeInFlight = false
    }

    /** Exponential backoff (2s/4s/8s/16s/30s, capped), retried indefinitely -- a user who expects
     * auto-reconnect wants it to keep trying, not give up after N attempts. Only [disconnect] (or
     * connecting to a different device) stops it. */
    private fun scheduleReconnect() {
        val device = lastDevice ?: return
        if (reconnectJob?.isActive == true) return
        reconnectAttempt++
        val delayMs = (2_000L * (1L shl (reconnectAttempt - 1).coerceAtMost(4))).coerceAtMost(30_000L)
        reconnectJob = scope.launch {
            delay(delayMs)
            if (userInitiatedDisconnect) return@launch
            teardownGatt()
            _state.value = BleConnectionState(
                status = BleStatus.CONNECTING,
                deviceName = device.name,
                deviceAddress = device.address,
            )
            gatt = device.connectGatt(context, false, gattCallback)
        }
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
                // Request high-throughput, low-latency connection interval (11.25ms - 15ms) for RTK correction streaming
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                // Request maximum BLE ATT MTU (517 bytes). Android and receiver will negotiate highest mutual MTU (up to 512B payload)
                val mtuRequested = g.requestMtu(MAX_MTU_REQUEST)
                if (!mtuRequested) {
                    g.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val errored = status != BluetoothGatt.GATT_SUCCESS
                _state.update {
                    it.copy(
                        status = if (errored) BleStatus.ERROR else BleStatus.DISCONNECTED,
                        errorMessage = if (errored) "GATT error $status" else null,
                    )
                }
                if (!userInitiatedDisconnect) scheduleReconnect()
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
                _state.update { it.copy(mtu = mtu) }
            }
            // Proceed to service discovery if not already discovered
            if (rxCharacteristic == null) {
                g.discoverServices()
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
            reconnectAttempt = 0
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
        _rawBytes.tryEmit(bytes)
        lineBuffer.append(String(bytes, Charsets.US_ASCII))
        while (true) {
            val newlineIdx = lineBuffer.indexOf("\n")
            if (newlineIdx < 0) {
                if (lineBuffer.length > MAX_LINE_BUFFER) lineBuffer.clear() // guard against a runaway buffer
                break
            }
            val line = lineBuffer.substring(0, newlineIdx)
            lineBuffer.delete(0, newlineIdx + 1)
            if (line.isNotBlank()) _rawLines.tryEmit(line.trimEnd('\r'))
            val sentence = NmeaParser.parse(line) ?: continue
            _nmeaSentences.tryEmit(sentence)
            when (sentence) {
                is NmeaSentence.Gga -> _state.update { it.copy(latestFix = sentence) }
                is NmeaSentence.Gst -> _state.update { it.copy(latestGst = sentence) }
                is NmeaSentence.Gsa -> _state.update { it.copy(latestGsa = sentence) }
                else -> Unit
            }
        }
    }

    companion object {
        private const val DEFAULT_MTU = 23 // BLE standard default before negotiation
        private const val MAX_MTU_REQUEST = 517 // Max ATT MTU supported by Android BLE (enables up to 512B payload)
        private const val ATT_WRITE_OVERHEAD = 3 // 1-byte OpCode + 2-byte Handle
        private const val MAX_LINE_BUFFER = 4096
    }
}
