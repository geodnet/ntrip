package com.geodnet.ntrip.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geodnet.ntrip.ble.BleConnectionState
import com.geodnet.ntrip.ble.BleDeviceInfo
import com.geodnet.ntrip.ble.BleRtkReceiver
import com.geodnet.ntrip.ble.BleScanner
import com.geodnet.ntrip.data.SettingsRepository
import com.geodnet.ntrip.ntrip.NtripConfig
import com.geodnet.ntrip.ntrip.NtripState
import com.geodnet.ntrip.rtcm.RtcmMessage
import com.geodnet.ntrip.rtcm.RtcmStats
import com.geodnet.ntrip.service.NtripForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NtripViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepository = SettingsRepository(app)

    private var service: NtripForegroundService? = null
    private var bound = false
    private var pendingStart: NtripConfig? = null

    private val _config = MutableStateFlow(NtripConfig())
    val config: StateFlow<NtripConfig> = _config.asStateFlow()

    private val _connectionState = MutableStateFlow(NtripState())
    val connectionState: StateFlow<NtripState> = _connectionState.asStateFlow()

    private val _rtcmStats = MutableStateFlow(RtcmStats())
    val rtcmStats: StateFlow<RtcmStats> = _rtcmStats.asStateFlow()

    private val _rtcmLog = MutableStateFlow<List<RtcmMessage>>(emptyList())
    val rtcmLog: StateFlow<List<RtcmMessage>> = _rtcmLog.asStateFlow()

    private val bleScanner = BleScanner(app)
    private val bleReceiver = BleRtkReceiver(app)
    val bleDevices: StateFlow<List<BleDeviceInfo>> = bleScanner.devices
    val bleIsScanning: StateFlow<Boolean> = bleScanner.isScanning
    val bleConnectionState: StateFlow<BleConnectionState> = bleReceiver.state

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val boundService = (binder as NtripForegroundService.LocalBinder).getService()
            service = boundService
            bound = true
            viewModelScope.launch {
                boundService.serviceState.collect { _connectionState.value = it }
            }
            viewModelScope.launch {
                boundService.rtcmStats.collect { _rtcmStats.value = it }
            }
            viewModelScope.launch {
                boundService.rtcmMessages.collect { message ->
                    _rtcmLog.update { (listOf(message) + it).take(RTCM_LOG_LIMIT) }
                }
            }
            // Forward every correction chunk from the caster to the BLE receiver, if connected.
            // sendRtcm() no-ops when there's no connected device, so this is safe to always run.
            viewModelScope.launch {
                boundService.rawBytes.collect { bytes -> bleReceiver.sendRtcm(bytes) }
            }
            pendingStart?.let {
                boundService.start(it)
                pendingStart = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    init {
        viewModelScope.launch {
            settingsRepository.configFlow.collect { _config.value = it }
        }
        val context = getApplication<Application>()
        context.bindService(
            Intent(context, NtripForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    fun updateConfig(newConfig: NtripConfig) {
        _config.value = newConfig
        viewModelScope.launch { settingsRepository.save(newConfig) }
    }

    fun connect() {
        _rtcmStats.value = RtcmStats()
        _rtcmLog.value = emptyList()
        val context = getApplication<Application>()
        ContextCompat.startForegroundService(context, Intent(context, NtripForegroundService::class.java))
        val current = service
        if (current != null) {
            current.start(_config.value)
        } else {
            pendingStart = _config.value
        }
    }

    fun disconnect() {
        service?.stopConnection()
    }

    fun isBluetoothEnabled(): Boolean = bleScanner.isBluetoothEnabled()

    fun startBleScan() = bleScanner.startScan()

    fun stopBleScan() = bleScanner.stopScan()

    fun connectBleDevice(address: String) {
        stopBleScan()
        val device = bleScanner.getDevice(address) ?: return
        bleReceiver.connect(device)
    }

    fun disconnectBleDevice() = bleReceiver.disconnect()

    override fun onCleared() {
        bleScanner.stopScan()
        bleReceiver.disconnect()
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        super.onCleared()
    }

    companion object {
        private const val RTCM_LOG_LIMIT = 200
    }
}
