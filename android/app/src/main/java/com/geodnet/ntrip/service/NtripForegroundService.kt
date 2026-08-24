package com.geodnet.ntrip.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.geodnet.ntrip.MainActivity
import com.geodnet.ntrip.ntrip.NtripClient
import com.geodnet.ntrip.ntrip.NtripConfig
import com.geodnet.ntrip.ntrip.NtripState
import com.geodnet.ntrip.ntrip.NtripStatus
import com.geodnet.ntrip.rtcm.RtcmMessage
import com.geodnet.ntrip.rtcm.RtcmStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service hosting the [NtripClient] connection so it survives the app being
 * backgrounded. The UI binds to this service (see MainActivity) and observes [serviceState].
 */
class NtripForegroundService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var client: NtripClient? = null
    private var runJob: Job? = null
    private var observeJob: Job? = null

    private val _serviceState = MutableStateFlow(NtripState())
    val serviceState: StateFlow<NtripState> = _serviceState.asStateFlow()

    private val _rtcmStats = MutableStateFlow(RtcmStats())
    val rtcmStats: StateFlow<RtcmStats> = _rtcmStats.asStateFlow()

    private val _rtcmMessages = MutableSharedFlow<RtcmMessage>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val rtcmMessages: SharedFlow<RtcmMessage> = _rtcmMessages.asSharedFlow()

    /** Raw correction bytes, for forwarding to a BLE RTK receiver (see NtripViewModel). */
    private val _rawBytes = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val rawBytes: SharedFlow<ByteArray> = _rawBytes.asSharedFlow()

    inner class LocalBinder : Binder() {
        fun getService(): NtripForegroundService = this@NtripForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun start(config: NtripConfig) {
        if (runJob?.isActive == true) return

        val newClient = NtripClient(config)
        client = newClient
        _serviceState.value = NtripState(status = NtripStatus.CONNECTING)
        _rtcmStats.value = RtcmStats()

        startForeground(NOTIFICATION_ID, buildNotification(_serviceState.value))

        observeJob = serviceScope.launch {
            launch {
                newClient.state.collect { state ->
                    _serviceState.value = state
                    updateNotification(state)
                }
            }
            launch { newClient.rtcmStats.collect { _rtcmStats.value = it } }
            launch { newClient.rtcmMessages.collect { _rtcmMessages.emit(it) } }
            launch { newClient.rawBytes.collect { _rawBytes.emit(it) } }
        }
        runJob = serviceScope.launch { newClient.run() }
    }

    fun stopConnection() {
        client?.stop()
        runJob?.cancel()
        observeJob?.cancel()
        client = null
        _serviceState.value = NtripState(status = NtripStatus.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        client?.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ntrip connection",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: NtripState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, openAppIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when (state.status) {
            NtripStatus.CONNECTING -> "Connecting..."
            NtripStatus.CONNECTED -> "Connected — ${state.bytesReceived} bytes received"
            NtripStatus.ERROR -> "Error: ${state.errorMessage}"
            NtripStatus.DISCONNECTED -> "Disconnected"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ntrip Client")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(state: NtripState) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(state))
    }

    companion object {
        private const val CHANNEL_ID = "ntrip_service"
        private const val NOTIFICATION_ID = 1
    }
}
