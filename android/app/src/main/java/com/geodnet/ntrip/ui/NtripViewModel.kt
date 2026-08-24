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
import com.geodnet.ntrip.data.SettingsRepository
import com.geodnet.ntrip.ntrip.NtripConfig
import com.geodnet.ntrip.ntrip.NtripState
import com.geodnet.ntrip.service.NtripForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val boundService = (binder as NtripForegroundService.LocalBinder).getService()
            service = boundService
            bound = true
            viewModelScope.launch {
                boundService.serviceState.collect { _connectionState.value = it }
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

    override fun onCleared() {
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        super.onCleared()
    }
}
