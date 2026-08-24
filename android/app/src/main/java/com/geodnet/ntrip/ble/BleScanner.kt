package com.geodnet.ntrip.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scans for nearby BLE devices so the user can pick their RTK receiver. Does not filter by the
 * Nordic UART Service UUID, since not every receiver advertises it in the scan response -- the
 * user picks by name/address instead. Assumes the caller has already obtained the scan
 * permission (BLUETOOTH_SCAN on API 31+, ACCESS_FINE_LOCATION below that).
 */
@SuppressLint("MissingPermission")
class BleScanner(private val context: Context) {

    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _devices = MutableStateFlow<List<BleDeviceInfo>>(emptyList())
    val devices: StateFlow<List<BleDeviceInfo>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val foundDevices = LinkedHashMap<String, BleDeviceInfo>()
    private var scanCallback: ScanCallback? = null

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        stopScan()
        foundDevices.clear()
        _devices.value = emptyList()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                foundDevices[device.address] = BleDeviceInfo(device.name, device.address, result.rssi)
                _devices.value = foundDevices.values.sortedByDescending { it.rssi }
            }

            override fun onScanFailed(errorCode: Int) {
                _isScanning.value = false
            }
        }
        scanCallback = callback
        scanner.startScan(callback)
        _isScanning.value = true
    }

    fun stopScan() {
        val scanner = adapter?.bluetoothLeScanner
        scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = null
        _isScanning.value = false
    }

    fun getDevice(address: String): BluetoothDevice? = adapter?.getRemoteDevice(address)
}
