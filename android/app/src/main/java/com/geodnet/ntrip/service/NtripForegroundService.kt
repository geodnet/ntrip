package com.geodnet.ntrip.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.geodnet.ntrip.MainActivity
import com.geodnet.ntrip.ble.NmeaSentence
import com.geodnet.ntrip.location.LocationFixAggregator
import com.geodnet.ntrip.location.MockLocationProvider
import com.geodnet.ntrip.location.MockLocationState
import com.geodnet.ntrip.location.PositionFix
import com.geodnet.ntrip.location.SegmentLogger
import com.geodnet.ntrip.location.StaticSegment
import com.geodnet.ntrip.location.StaticSegmentDetector
import com.geodnet.ntrip.location.TrajectoryBuffer
import com.geodnet.ntrip.logging.GnssRawLogger
import com.geodnet.ntrip.logging.GnssRawLoggerState
import com.geodnet.ntrip.logging.RawBinaryLogger
import com.geodnet.ntrip.logging.RawLoggerState
import com.geodnet.ntrip.net.TcpBroadcastServer
import com.geodnet.ntrip.net.TcpServerState
import com.geodnet.ntrip.ntrip.GgaPositionOverride
import com.geodnet.ntrip.ntrip.NtripClient
import com.geodnet.ntrip.ntrip.NtripConfig
import com.geodnet.ntrip.ntrip.NtripState
import com.geodnet.ntrip.ntrip.NtripStatus
import com.geodnet.ntrip.rtcm.BaseStationFix
import com.geodnet.ntrip.rtcm.EpochLatencyStats
import com.geodnet.ntrip.rtcm.RtcmFrame
import com.geodnet.ntrip.rtcm.RtcmMessage
import com.geodnet.ntrip.rtcm.RtcmStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private val _epochStats = MutableStateFlow(EpochLatencyStats())
    val epochStats: StateFlow<EpochLatencyStats> = _epochStats.asStateFlow()

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

    /** CRC-valid RTCM frames (whole, exact bytes) for BLE forwarding with optional ephemeris
     * filtering -- see readme.md's "GNSS Ephemeris Filtering" and NtripViewModel, which does the
     * actual filtering (it owns the toggle) and calls `bleReceiver.sendRtcm()`. */
    private val _rtcmFrames = MutableSharedFlow<RtcmFrame>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val rtcmFrames: SharedFlow<RtcmFrame> = _rtcmFrames.asSharedFlow()

    // Best-fix aggregation (BLE receiver, falling back to phone GPS) and its two consumers: the
    // mock location provider and the NMEA TCP server. The RTCM TCP server just rebroadcasts
    // _rawBytes above. All three run independently of the caster connect/disconnect lifecycle.
    //
    // `by lazy`, not eager `val`s: these two touch getSystemService() in their constructors, and
    // a Service's base Context isn't attached yet while its own field initializers run (Android
    // constructs the Service via a bare newInstance() and only calls attachBaseContext()
    // afterwards, before onCreate()) -- an eager `val = LocationFixAggregator(this)` here would
    // NPE inside getSystemService(). Deferring construction until first use (from onCreate()
    // onward) sidesteps that.
    private val locationAggregator by lazy { LocationFixAggregator(this) }
    private val mockLocationProvider by lazy { MockLocationProvider(this) }
    private val nmeaServer = TcpBroadcastServer(NMEA_PORT)
    private val rtcmServer = TcpBroadcastServer(RTCM_PORT)

    val bestFix: StateFlow<PositionFix?> by lazy { locationAggregator.fix }
    val mockLocationState: StateFlow<MockLocationState> by lazy { mockLocationProvider.state }
    val nmeaServerState: StateFlow<TcpServerState> = nmeaServer.state
    val rtcmServerState: StateFlow<TcpServerState> = rtcmServer.state

    // Map screen support: the rover trajectory and static-segment detection both key off the same
    // "best fix" stream as the mock location provider, independent of the caster connect/disconnect
    // lifecycle (a rover position exists whether or not corrections are flowing). The base station
    // position, in contrast, only exists while connected -- it's decoded from the caster's own
    // 1005/1006 RTCM frames -- so it's re-wired per NtripClient instance in start()/stopConnection().
    private val trajectoryBuffer = TrajectoryBuffer()
    private val staticSegmentDetector = StaticSegmentDetector()
    private val segmentLogger by lazy { SegmentLogger(this) }
    private val _staticSegments = MutableStateFlow<List<StaticSegment>>(emptyList())
    private val _baseStation = MutableStateFlow<BaseStationFix?>(null)

    val trajectory: StateFlow<List<PositionFix>> = trajectoryBuffer.points
    val staticSegments: StateFlow<List<StaticSegment>> = _staticSegments.asStateFlow()
    val baseStation: StateFlow<BaseStationFix?> = _baseStation.asStateFlow()

    fun clearTrajectory() = trajectoryBuffer.clear()

    // Dual Data Logger (readme.md section 6): the raw binary stream logger (caster RTCM + BLE raw
    // bytes) and the phone's own GNSS chipset raw-measurement/nav-message/IMU logger. Both are
    // `by lazy` for the same Context-not-attached-yet-in-field-initializers reason as
    // locationAggregator/mockLocationProvider above.
    private val rawBinaryLogger by lazy { RawBinaryLogger(this) }
    private val gnssRawLogger by lazy { GnssRawLogger(this) }
    private var currentConfig: NtripConfig? = null
    private var connectedBleId: String? = null
    private var connectedBleName: String? = null

    val rawLoggerState: StateFlow<RawLoggerState> by lazy { rawBinaryLogger.state }
    val gnssRawLoggerState: StateFlow<GnssRawLoggerState> by lazy { gnssRawLogger.state }

    fun updateConfig(config: NtripConfig) {
        currentConfig = config
        segmentLogger.setMountpoint(config.mountpoint)
    }

    fun setRawLoggingEnabled(enabled: Boolean) {
        if (enabled) {
            val mountpoint = currentConfig?.mountpoint?.ifBlank { "AUTO" } ?: "AUTO"
            val roverId = connectedBleName?.ifBlank { null } ?: connectedBleId?.ifBlank { null } ?: "rover"
            rawBinaryLogger.start(mountpoint, roverId)
        } else {
            rawBinaryLogger.stop()
        }
    }

    fun setGnssRawLoggingEnabled(enabled: Boolean) {
        if (enabled) gnssRawLogger.start() else gnssRawLogger.stop()
    }

    /** Live position for the caster GGA upload / baseline reference -- see NtripClient's
     * `livePosition` param and readme.md's "Smart Phone Location GGA Fallback". Null (falling
     * back to the configured static lat/lon) only until the first BLE or phone fix arrives. */
    private fun currentGgaOverride(): GgaPositionOverride? = locationAggregator.fix.value?.let {
        GgaPositionOverride(it.latitude, it.longitude, it.altitudeM, it.numSatellites, it.hdop)
    }

    inner class LocalBinder : Binder() {
        fun getService(): NtripForegroundService = this@NtripForegroundService
    }

    private val soundNotifier by lazy { com.geodnet.ntrip.audio.RtkSoundNotifier() }
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNetworkCallback()
        serviceScope.launch { locationAggregator.fix.collect { mockLocationProvider.update(it) } }
        serviceScope.launch {
            locationAggregator.fix.collect { fix ->
                if (fix != null) {
                    soundNotifier.onFixQualityChanged(fix.fixQuality)
                }
            }
        }
        serviceScope.launch {
            locationAggregator.nmeaLine.collect { line ->
                nmeaServer.broadcast((line + "\n").toByteArray(Charsets.US_ASCII))
            }
        }
        serviceScope.launch { _rawBytes.collect { rtcmServer.broadcast(it) } }
        serviceScope.launch {
            locationAggregator.fix.collect { fix ->
                if (fix == null) return@collect
                trajectoryBuffer.add(fix)
                staticSegmentDetector.accept(fix)?.let { segment ->
                    _staticSegments.update { list ->
                        val idx = list.indexOfFirst { it.startTimeMs == segment.startTimeMs }
                        if (idx >= 0) {
                            list.toMutableList().apply { set(idx, segment) }
                        } else {
                            list + segment
                        }
                    }
                    segmentLogger.append(segment)
                }
                // Live real-time static detection: update ongoing static segment if currently stationary >= minDuration
                staticSegmentDetector.currentSegment()?.let { activeSeg ->
                    _staticSegments.update { list ->
                        val idx = list.indexOfFirst { it.startTimeMs == activeSeg.startTimeMs }
                        if (idx >= 0) {
                            list.toMutableList().apply { set(idx, activeSeg) }
                        } else {
                            list + activeSeg
                        }
                    }
                }
            }
        }
        locationAggregator.startPhoneUpdates()
    }

    /** Called by NtripViewModel, which owns the (Activity-scoped) BLE receiver -- see
     * android/CLAUDE.md's BLE-not-service-hosted gap. [deviceId] (address) / [deviceName] is remembered as the
     * Raw Binary Stream Logger's roverId. */
    fun onBleConnectionChanged(connected: Boolean, deviceId: String? = null, deviceName: String? = null) {
        locationAggregator.onBleConnectionChanged(connected)
        if (!connected) soundNotifier.reset()
        connectedBleId = if (connected) deviceId else null
        connectedBleName = if (connected) deviceName else null
        if (connected && rawLoggerState.value.active) {
            val mountpoint = currentConfig?.mountpoint?.ifBlank { "AUTO" } ?: "AUTO"
            val roverId = deviceName?.ifBlank { null } ?: deviceId?.ifBlank { null } ?: "rover"
            rawBinaryLogger.start(mountpoint, roverId)
        }
    }
    fun onBleFix(sentence: NmeaSentence.Gga) = locationAggregator.onBleFix(sentence)
    fun onBleRawLine(line: String) = locationAggregator.onBleRawLine(line)
    fun onBleRawBytes(bytes: ByteArray) = rawBinaryLogger.logRoveBytes(bytes)

    fun setMockLocationEnabled(enabled: Boolean) {
        if (enabled) mockLocationProvider.enable() else mockLocationProvider.disable()
    }

    fun setNmeaServerEnabled(enabled: Boolean) {
        if (enabled) nmeaServer.start(serviceScope) else nmeaServer.stop()
    }

    fun setRtcmServerEnabled(enabled: Boolean) {
        if (enabled) rtcmServer.start(serviceScope) else rtcmServer.stop()
    }

    fun setSoundAlertsEnabled(enabled: Boolean) {
        soundNotifier.isEnabled = enabled
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    /** Set by [stopConnection], checked before auto-reconnecting -- an unexpected drop schedules
     * a retry, but the user explicitly disconnecting must not. */
    private var userInitiatedDisconnect = false
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    fun start(config: NtripConfig) {
        if (runJob?.isActive == true && currentConfig == config) return
        userInitiatedDisconnect = false
        reconnectJob?.cancel()
        reconnectAttempt = 0
        startInternal(config)
    }

    private fun startInternal(config: NtripConfig) {
        runJob?.cancel()
        observeJob?.cancel()
        client?.stop()

        val newClient = NtripClient(config, livePosition = ::currentGgaOverride)
        client = newClient
        currentConfig = config
        segmentLogger.setMountpoint(config.mountpoint)
        if (rawLoggerState.value.active) {
            val mountpoint = config.mountpoint.ifBlank { "AUTO" }
            val roverId = connectedBleName?.ifBlank { null } ?: connectedBleId?.ifBlank { null } ?: "rover"
            rawBinaryLogger.start(mountpoint, roverId)
        }
        _serviceState.value = NtripState(status = NtripStatus.CONNECTING)
        _rtcmStats.value = RtcmStats()
        _epochStats.value = EpochLatencyStats()

        startForeground(NOTIFICATION_ID, buildNotification(_serviceState.value))

        observeJob = serviceScope.launch {
            launch {
                newClient.state.collect { state ->
                    _serviceState.value = state
                    updateNotification(state)
                    if (state.status == NtripStatus.CONNECTED) {
                        reconnectJob?.cancel()
                        reconnectAttempt = 0
                    }
                }
            }
            launch { newClient.rtcmStats.collect { _rtcmStats.value = it } }
            launch { newClient.epochStats.collect { _epochStats.value = it } }
            launch { newClient.rtcmMessages.collect { _rtcmMessages.emit(it) } }
            launch { newClient.rawBytes.collect { _rawBytes.emit(it) } }
            launch { newClient.rawBytes.collect { rawBinaryLogger.logBaseBytes(it) } }
            launch { newClient.baseStation.collect { _baseStation.value = it } }
            launch { newClient.rtcmFrames.collect { _rtcmFrames.emit(it) } }
        }
        runJob = serviceScope.launch {
            try {
                newClient.run()
            } finally {
                if (!userInitiatedDisconnect && _serviceState.value.status != NtripStatus.CONNECTED) {
                    scheduleReconnect(config)
                }
            }
        }
    }

    /** Exponential backoff (2s/4s/8s/16s/30s, capped) rather than hammering the caster after a
     * network blip or the caster itself briefly bouncing everyone. Keeps retrying indefinitely --
     * a user who wants auto-reconnect wants it to keep trying, not give up after N attempts -- the
     * only way out is calling [stopConnection] (or starting a different config, which cancels this
     * job too via [start]). */
    private fun scheduleReconnect(config: NtripConfig) {
        if (reconnectJob?.isActive == true || userInitiatedDisconnect) return
        reconnectAttempt++
        val delayMs = (2_000L * (1L shl (reconnectAttempt - 1).coerceAtMost(4))).coerceAtMost(30_000L)
        reconnectJob = serviceScope.launch {
            delay(delayMs)
            if (!userInitiatedDisconnect) startInternal(config)
        }
    }

    fun stopConnection() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        client?.stop()
        runJob?.cancel()
        observeJob?.cancel()
        client = null
        _baseStation.value = null
        soundNotifier.reset()
        _serviceState.value = NtripState(status = NtripStatus.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Internet connection restored -- immediately trigger reconnect if session was active
                serviceScope.launch {
                    val cfg = currentConfig
                    if (cfg != null && !userInitiatedDisconnect && _serviceState.value.status != NtripStatus.CONNECTED) {
                        reconnectJob?.cancel()
                        reconnectAttempt = 0
                        startInternal(cfg)
                    }
                }
            }

            override fun onLost(network: Network) {
                // Internet connection dropped
                serviceScope.launch {
                    if (!userInitiatedDisconnect && _serviceState.value.status == NtripStatus.CONNECTED) {
                        _serviceState.value = NtripState(
                            status = NtripStatus.ERROR,
                            errorMessage = "Internet disconnected — waiting for network...",
                            bytesReceived = _serviceState.value.bytesReceived
                        )
                        updateNotification(_serviceState.value)
                        client?.stop()
                    }
                }
            }
        }
        networkCallback = callback
        try {
            connectivityManager?.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            // Fallback for mock/test environments where network callback registration might be restricted
        }
    }

    override fun onDestroy() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        networkCallback?.let { callback ->
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
        networkCallback = null
        client?.stop()
        staticSegmentDetector.flush()?.let { segment ->
            _staticSegments.update { it + segment }
            segmentLogger.append(segment)
        }
        locationAggregator.stopPhoneUpdates()
        mockLocationProvider.disable()
        nmeaServer.stop()
        rtcmServer.stop()
        rawBinaryLogger.stop()
        gnssRawLogger.stop()
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

        /** readme.md's fixed local ports for the NMEA/RTCM TCP bridges (SW Maps integration). */
        const val NMEA_PORT = 10110
        const val RTCM_PORT = 10120
    }
}
