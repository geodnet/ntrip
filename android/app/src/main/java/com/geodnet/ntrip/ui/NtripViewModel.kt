package com.geodnet.ntrip.ui

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geodnet.ntrip.ble.BleConnectionState
import com.geodnet.ntrip.ble.BleDeviceInfo
import com.geodnet.ntrip.ble.BleRtkReceiver
import com.geodnet.ntrip.ble.BleScanner
import com.geodnet.ntrip.ble.BleStatus
import com.geodnet.ntrip.data.GeodnetCoverageRepository
import com.geodnet.ntrip.data.NearbyStation
import com.geodnet.ntrip.data.NtripProfileRepository
import com.geodnet.ntrip.data.OutputSettings
import com.geodnet.ntrip.data.SettingsRepository
import com.geodnet.ntrip.location.MockLocationState
import com.geodnet.ntrip.location.PositionFix
import com.geodnet.ntrip.location.StaticSegment
import com.geodnet.ntrip.logging.GnssRawLoggerState
import com.geodnet.ntrip.logging.RawLoggerState
import com.geodnet.ntrip.net.TcpServerState
import com.geodnet.ntrip.ntrip.NtripConfig
import com.geodnet.ntrip.ntrip.NtripProfile
import com.geodnet.ntrip.ntrip.NtripState
import com.geodnet.ntrip.ntrip.NtripStatus
import com.geodnet.ntrip.rtcm.BaseStationFix
import com.geodnet.ntrip.rtcm.EPHEMERIS_FILTER_TYPES
import com.geodnet.ntrip.rtcm.EpochLatencyStats
import com.geodnet.ntrip.rtcm.RtcmMessage
import com.geodnet.ntrip.rtcm.RtcmStats
import com.geodnet.ntrip.service.NtripForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NtripViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepository = SettingsRepository(app)
    private val profileRepository = NtripProfileRepository(app)

    private var service: NtripForegroundService? = null
    private var bound = false
    private var pendingStart: NtripConfig? = null
    private var pendingOutputSettings: OutputSettings? = null

    private val _config = MutableStateFlow(NtripConfig())
    val config: StateFlow<NtripConfig> = _config.asStateFlow()

    private val _profiles = MutableStateFlow<List<NtripProfile>>(emptyList())
    val profiles: StateFlow<List<NtripProfile>> = _profiles.asStateFlow()

    private val _selectedProfileId = MutableStateFlow<String?>(null)
    val selectedProfileId: StateFlow<String?> = _selectedProfileId.asStateFlow()

    private val _connectionState = MutableStateFlow(NtripState())
    val connectionState: StateFlow<NtripState> = _connectionState.asStateFlow()

    private val _rtcmStats = MutableStateFlow(RtcmStats())
    val rtcmStats: StateFlow<RtcmStats> = _rtcmStats.asStateFlow()

    private val _epochStats = MutableStateFlow(EpochLatencyStats())
    val epochStats: StateFlow<EpochLatencyStats> = _epochStats.asStateFlow()

    private val _rtcmLog = MutableStateFlow<List<RtcmMessage>>(emptyList())
    val rtcmLog: StateFlow<List<RtcmMessage>> = _rtcmLog.asStateFlow()

    private val bleScanner = BleScanner(app)
    private val bleReceiver = BleRtkReceiver(app)
    val bleDevices: StateFlow<List<BleDeviceInfo>> = bleScanner.devices
    val bleIsScanning: StateFlow<Boolean> = bleScanner.isScanning
    val bleConnectionState: StateFlow<BleConnectionState> = bleReceiver.state

    private val _lastBleName = MutableStateFlow<String?>(null)
    val lastBleName: StateFlow<String?> = _lastBleName.asStateFlow()

    private val _lastBleAddress = MutableStateFlow<String?>(null)
    val lastBleAddress: StateFlow<String?> = _lastBleAddress.asStateFlow()

    private val _bestFix = MutableStateFlow<PositionFix?>(null)
    val bestFix: StateFlow<PositionFix?> = _bestFix.asStateFlow()

    private val _mockLocationState = MutableStateFlow(MockLocationState())
    val mockLocationState: StateFlow<MockLocationState> = _mockLocationState.asStateFlow()

    private val _nmeaServerState = MutableStateFlow(TcpServerState(port = NtripForegroundService.NMEA_PORT))
    val nmeaServerState: StateFlow<TcpServerState> = _nmeaServerState.asStateFlow()

    private val _rtcmServerState = MutableStateFlow(TcpServerState(port = NtripForegroundService.RTCM_PORT))
    val rtcmServerState: StateFlow<TcpServerState> = _rtcmServerState.asStateFlow()

    // Map screen state. trajectory/staticSegments/baseStation mirror the service's (survive
    // rotation/tab-nav/WebView-reload via the service, not this ViewModel -- see
    // NtripForegroundService). showBaseStation is pure view state with no service-side effect,
    // so it's just loaded/persisted directly here rather than round-tripped through the service.
    private val _trajectory = MutableStateFlow<List<PositionFix>>(emptyList())
    val trajectory: StateFlow<List<PositionFix>> = _trajectory.asStateFlow()

    private val _staticSegments = MutableStateFlow<List<StaticSegment>>(emptyList())
    val staticSegments: StateFlow<List<StaticSegment>> = _staticSegments.asStateFlow()

    private val _baseStation = MutableStateFlow<BaseStationFix?>(null)
    val baseStation: StateFlow<BaseStationFix?> = _baseStation.asStateFlow()

    private val _showBaseStation = MutableStateFlow(false)
    val showBaseStation: StateFlow<Boolean> = _showBaseStation.asStateFlow()

    private val _filterEphemerisForBle = MutableStateFlow(true)
    val filterEphemerisForBle: StateFlow<Boolean> = _filterEphemerisForBle.asStateFlow()

    private val _soundAlertsEnabled = MutableStateFlow(true)
    val soundAlertsEnabled: StateFlow<Boolean> = _soundAlertsEnabled.asStateFlow()

    private val coverageRepository = GeodnetCoverageRepository(app)

    private val _nearbyStations = MutableStateFlow<List<NearbyStation>>(emptyList())
    val nearbyStations: StateFlow<List<NearbyStation>> = _nearbyStations.asStateFlow()

    private val _isCoverageLoading = MutableStateFlow(false)
    val isCoverageLoading: StateFlow<Boolean> = _isCoverageLoading.asStateFlow()

    private val _showNearbyStations = MutableStateFlow(true)
    val showNearbyStations: StateFlow<Boolean> = _showNearbyStations.asStateFlow()

    // NTRIP Caster Sourcetable
    private val _sourcetable = MutableStateFlow<com.geodnet.ntrip.ntrip.NtripSourcetable?>(null)
    val sourcetable: StateFlow<com.geodnet.ntrip.ntrip.NtripSourcetable?> = _sourcetable.asStateFlow()

    private val _isSourcetableLoading = MutableStateFlow(false)
    val isSourcetableLoading: StateFlow<Boolean> = _isSourcetableLoading.asStateFlow()

    private val _sourcetableError = MutableStateFlow<String?>(null)
    val sourcetableError: StateFlow<String?> = _sourcetableError.asStateFlow()

    // rawLoggerState/gnssRawLoggerState (mirroring the service's, like mockLocationState above)
    // are the source of truth for whether logging is on -- .active is what the UI switches bind
    // to, same pattern as mockLocationState.enabled.
    private val _rawLoggerState = MutableStateFlow(RawLoggerState())
    val rawLoggerState: StateFlow<RawLoggerState> = _rawLoggerState.asStateFlow()

    private val _gnssRawLoggerState = MutableStateFlow(GnssRawLoggerState())
    val gnssRawLoggerState: StateFlow<GnssRawLoggerState> = _gnssRawLoggerState.asStateFlow()

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
                boundService.epochStats.collect { _epochStats.value = it }
            }
            viewModelScope.launch {
                boundService.rtcmMessages.collect { message ->
                    _rtcmLog.update { (listOf(message) + it).take(RTCM_LOG_LIMIT) }
                }
            }
            // Forward every CRC-valid RTCM frame from the caster to the BLE receiver, if
            // connected -- sendRtcm() no-ops when there's no connected device, so this is safe to
            // always run. Filtered by frame, not raw byte chunk, so filterEphemerisForBle can drop
            // whole ephemeris frames (readme.md's "GNSS Ephemeris Filtering") without corrupting
            // the RTCM stream the receiver sees.
            viewModelScope.launch {
                boundService.rtcmFrames.collect { frame ->
                    if (_filterEphemerisForBle.value && frame.msgType in EPHEMERIS_FILTER_TYPES) return@collect
                    bleReceiver.sendRtcm(frame.bytes)
                }
            }
            viewModelScope.launch { boundService.bestFix.collect { _bestFix.value = it } }
            viewModelScope.launch { boundService.mockLocationState.collect { _mockLocationState.value = it } }
            viewModelScope.launch { boundService.nmeaServerState.collect { _nmeaServerState.value = it } }
            viewModelScope.launch { boundService.rtcmServerState.collect { _rtcmServerState.value = it } }
            viewModelScope.launch { boundService.trajectory.collect { _trajectory.value = it } }
            viewModelScope.launch { boundService.staticSegments.collect { _staticSegments.value = it } }
            viewModelScope.launch { boundService.baseStation.collect { _baseStation.value = it } }
            viewModelScope.launch { boundService.rawLoggerState.collect { _rawLoggerState.value = it } }
            viewModelScope.launch { boundService.gnssRawLoggerState.collect { _gnssRawLoggerState.value = it } }
            // Sync whatever BLE state already exists (e.g. connected before the service finished
            // binding) rather than waiting for the next bleReceiver.state change to report it.
            bleReceiver.state.value.let { state ->
                boundService.onBleConnectionChanged(state.status == BleStatus.CONNECTED, state.deviceAddress, state.deviceName)
                state.latestFix?.let { boundService.onBleFix(it) }
            }
            boundService.updateConfig(_config.value)
            pendingOutputSettings?.let {
                applyOutputSettings(boundService, it)
                pendingOutputSettings = null
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
            settingsRepository.configFlow.collect {
                _config.value = it
                service?.updateConfig(it)
            }
        }
        viewModelScope.launch {
            profileRepository.profilesFlow.collect { _profiles.value = it }
        }
        viewModelScope.launch {
            profileRepository.selectedProfileIdFlow.collect { _selectedProfileId.value = it }
        }
        viewModelScope.launch {
            settingsRepository.lastBleDeviceFlow.collect { (name, addr) ->
                _lastBleName.value = name
                _lastBleAddress.value = addr
            }
        }
        // Bridge BLE receiver events into the (Activity-independent) foreground service, which
        // owns the mock-location/NMEA-server "best fix" aggregation -- see android/CLAUDE.md's
        // note on the BLE receiver being ViewModel-scoped rather than service-hosted.
        viewModelScope.launch {
            bleReceiver.state.collect { state ->
                service?.onBleConnectionChanged(state.status == BleStatus.CONNECTED, state.deviceAddress, state.deviceName)
                state.latestFix?.let { service?.onBleFix(it) }
                if (state.status == BleStatus.CONNECTED && state.deviceAddress != null) {
                    settingsRepository.saveLastBleDevice(state.deviceName, state.deviceAddress)
                }
            }
        }
        viewModelScope.launch {
            bleReceiver.nmeaSentences.collect { sentence ->
                if (sentence is com.geodnet.ntrip.ble.NmeaSentence.Gga) {
                    service?.onBleFix(sentence)
                }
            }
        }
        viewModelScope.launch {
            bleReceiver.rawLines.collect { line -> service?.onBleRawLine(line) }
        }
        viewModelScope.launch {
            bleReceiver.rawBytes.collect { bytes -> service?.onBleRawBytes(bytes) }
        }
        // Re-apply whatever was enabled last session -- these toggles otherwise default to off on
        // every service (re)start, unlike NtripConfig.
        viewModelScope.launch {
            val persisted = settingsRepository.outputSettingsFlow.first()
            val current = service
            if (current != null) applyOutputSettings(current, persisted) else pendingOutputSettings = persisted
        }
        viewModelScope.launch {
            settingsRepository.outputSettingsFlow.collect {
                _showBaseStation.value = it.showBaseStation
                _filterEphemerisForBle.value = it.filterEphemerisForBle
                _soundAlertsEnabled.value = it.soundAlertsEnabled
            }
        }
        viewModelScope.launch {
            loadCoverageStations()
        }
        viewModelScope.launch {
            _bestFix.collect { fix ->
                if (fix != null) {
                    updateNearbyStations(fix.latitude, fix.longitude)
                } else {
                    val phoneLoc = getPhoneLastLocation()
                    if (phoneLoc != null) {
                        updateNearbyStations(phoneLoc.latitude, phoneLoc.longitude)
                    } else {
                        val cfg = _config.value
                        if (cfg.latitude != 0.0 || cfg.longitude != 0.0) {
                            updateNearbyStations(cfg.latitude, cfg.longitude)
                        }
                    }
                }
            }
        }
        val context = getApplication<Application>()
        context.bindService(
            Intent(context, NtripForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun applyOutputSettings(svc: NtripForegroundService, settings: OutputSettings) {
        svc.setMockLocationEnabled(settings.mockLocationEnabled)
        svc.setNmeaServerEnabled(settings.nmeaServerEnabled)
        svc.setRtcmServerEnabled(settings.rtcmServerEnabled)
        svc.setRawLoggingEnabled(settings.rawLoggingEnabled)
        svc.setGnssRawLoggingEnabled(settings.gnssRawLoggingEnabled)
        svc.setSoundAlertsEnabled(settings.soundAlertsEnabled)
    }

    fun setMockLocationEnabled(enabled: Boolean) {
        service?.setMockLocationEnabled(enabled)
        viewModelScope.launch { settingsRepository.saveMockLocationEnabled(enabled) }
    }

    fun setNmeaServerEnabled(enabled: Boolean) {
        service?.setNmeaServerEnabled(enabled)
        viewModelScope.launch { settingsRepository.saveNmeaServerEnabled(enabled) }
    }

    fun setRtcmServerEnabled(enabled: Boolean) {
        service?.setRtcmServerEnabled(enabled)
        viewModelScope.launch { settingsRepository.saveRtcmServerEnabled(enabled) }
    }

    fun setShowBaseStation(enabled: Boolean) {
        _showBaseStation.value = enabled
        viewModelScope.launch { settingsRepository.saveShowBaseStation(enabled) }
    }

    fun setFilterEphemerisForBle(enabled: Boolean) {
        _filterEphemerisForBle.value = enabled
        viewModelScope.launch { settingsRepository.saveFilterEphemerisForBle(enabled) }
    }

    fun setSoundAlertsEnabled(enabled: Boolean) {
        _soundAlertsEnabled.value = enabled
        service?.setSoundAlertsEnabled(enabled)
        viewModelScope.launch { settingsRepository.saveSoundAlertsEnabled(enabled) }
    }

    fun setRawLoggingEnabled(enabled: Boolean) {
        service?.setRawLoggingEnabled(enabled)
        viewModelScope.launch { settingsRepository.saveRawLoggingEnabled(enabled) }
    }

    fun setGnssRawLoggingEnabled(enabled: Boolean) {
        service?.setGnssRawLoggingEnabled(enabled)
        viewModelScope.launch { settingsRepository.saveGnssRawLoggingEnabled(enabled) }
    }

    fun clearTrajectory() = service?.clearTrajectory() ?: Unit

    fun updateConfig(newConfig: NtripConfig) {
        _config.value = newConfig
        viewModelScope.launch { settingsRepository.save(newConfig) }
    }

    /** Loads a saved profile into the current working config (and marks it selected, enabling
     * [updateSelectedProfile]) -- does not connect by itself. */
    fun loadProfile(profile: NtripProfile) {
        updateConfig(profile.config)
        viewModelScope.launch { profileRepository.setSelectedProfileId(profile.id) }
    }

    /** Generates a non-colliding default profile name like "Profile 1", "Profile 2", etc. */
    fun generateUniqueProfileName(base: String = "Profile"): String {
        val existingNames = _profiles.value.map { it.name.trim().lowercase() }.toSet()
        var index = 1
        while (existingNames.contains("${base.lowercase()} $index") || existingNames.contains("${base.lowercase()}-$index")) {
            index++
        }
        return "$base $index"
    }

    /** Saves [config] as a profile named [name] (or generates a unique name if blank).
     * If a profile with this exact name already exists, it updates that record to guarantee uniqueness. */
    fun saveAsNewProfile(name: String, config: NtripConfig) {
        val trimmed = name.trim().ifBlank { generateUniqueProfileName() }
        updateConfig(config)
        viewModelScope.launch { profileRepository.addProfile(trimmed, config) }
    }

    /** Overwrites the currently-selected profile's name/config with [name]/[config]. No-ops if no
     * profile is selected -- the UI only enables this action when one is. */
    fun updateSelectedProfile(name: String, config: NtripConfig) {
        val id = _selectedProfileId.value ?: return
        val trimmed = name.trim().ifBlank { "Profile" }
        updateConfig(config)
        viewModelScope.launch { profileRepository.updateProfile(id, trimmed, config) }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { profileRepository.deleteProfile(id) }
    }

    fun connect() {
        _rtcmStats.value = RtcmStats()
        _rtcmLog.value = emptyList()
        _epochStats.value = EpochLatencyStats()
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
        val device = bleScanner.getDevice(address)
            ?: try {
                val bluetoothManager = getApplication<Application>().getSystemService(android.bluetooth.BluetoothManager::class.java)
                bluetoothManager?.adapter?.getRemoteDevice(address)
            } catch (_: Exception) { null }
            ?: return
        bleReceiver.connect(device)
    }

    fun disconnectBleDevice() = bleReceiver.disconnect()

    private fun getPhoneLastLocation(): Location? {
        val context = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
    }

    suspend fun loadCoverageStations(forceRefresh: Boolean = false) {
        _isCoverageLoading.value = true
        try {
            coverageRepository.loadStations(forceRefresh)
            val fix = _bestFix.value
            if (fix != null) {
                updateNearbyStations(fix.latitude, fix.longitude)
            } else {
                val phoneLoc = getPhoneLastLocation()
                if (phoneLoc != null) {
                    updateNearbyStations(phoneLoc.latitude, phoneLoc.longitude)
                } else {
                    val cfg = _config.value
                    if (cfg.latitude != 0.0 || cfg.longitude != 0.0) {
                        updateNearbyStations(cfg.latitude, cfg.longitude)
                    }
                }
            }
        } finally {
            _isCoverageLoading.value = false
        }
    }

    fun refreshCoverageStations() {
        viewModelScope.launch {
            loadCoverageStations(forceRefresh = true)
        }
    }

    private var lastNearbyCheckLat: Double? = null
    private var lastNearbyCheckLon: Double? = null

    private fun updateNearbyStations(lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) return
        val lastLat = lastNearbyCheckLat
        val lastLon = lastNearbyCheckLon
        if (lastLat != null && lastLon != null && _nearbyStations.value.isNotEmpty()) {
            val dist = GeodnetCoverageRepository.haversineDistanceKm(lat, lon, lastLat, lastLon)
            if (dist < 1.0) return
        }
        lastNearbyCheckLat = lat
        lastNearbyCheckLon = lon
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val list = coverageRepository.findNearbyStations(
                userLat = lat,
                userLon = lon,
                maxRadiusKm = 100.0,
                limit = 20
            )
            _nearbyStations.value = list
        }
    }

    fun setShowNearbyStations(enabled: Boolean) {
        _showNearbyStations.value = enabled
    }

    /**
     * Switches the active base station for GEODNET RTK:
     * Does NOT change the caster mountpoint (keeps AUTO / configured mountpoint intact)
     * and updates the GGA uploaded coordinates to the selected base station's coordinates.
     */
    fun applyBaseCoordinate(lat: Double, lon: Double, alt: Double? = null) {
        val current = _config.value
        val updated = current.copy(
            latitude = lat,
            longitude = lon,
            altitude = alt ?: current.altitude,
            useLiveLocation = false,
        )
        updateConfig(updated)

        val currentStatus = _connectionState.value.status
        if (currentStatus == NtripStatus.CONNECTED || currentStatus == NtripStatus.CONNECTING) {
            service?.stopConnection()
            service?.start(updated)
        }
    }

    /**
     * Applies a discovered base station or sourcetable mountpoint.
     * When selecting a GEODNET base station (lat/lon present), it keeps the mountpoint
     * intact and uploads the station's coordinates in GGA.
     */
    fun applyMountpoint(mountpoint: String, lat: Double? = null, lon: Double? = null) {
        val targetStation = _nearbyStations.value.find {
            it.name.equals(mountpoint, ignoreCase = true) || it.shortName.equals(mountpoint, ignoreCase = true)
        }
        val targetLat = lat ?: targetStation?.lat
        val targetLon = lon ?: targetStation?.lng

        if (targetLat != null && targetLon != null) {
            applyBaseCoordinate(targetLat, targetLon)
        } else {
            val current = _config.value
            val updated = current.copy(mountpoint = mountpoint)
            updateConfig(updated)

            val currentStatus = _connectionState.value.status
            if (currentStatus == NtripStatus.CONNECTED || currentStatus == NtripStatus.CONNECTING) {
                service?.stopConnection()
                service?.start(updated)
            }
        }
    }

    /**
     * Resets the GGA upload back to the live rover GPS position.
     */
    fun resetToLiveLocation() {
        val current = _config.value
        val best = _bestFix.value
        val updated = if (best != null) {
            current.copy(
                latitude = best.latitude,
                longitude = best.longitude,
                altitude = best.altitudeM,
                useLiveLocation = true,
            )
        } else {
            current.copy(useLiveLocation = true)
        }
        updateConfig(updated)

        val currentStatus = _connectionState.value.status
        if (currentStatus == NtripStatus.CONNECTED || currentStatus == NtripStatus.CONNECTING) {
            service?.stopConnection()
            service?.start(updated)
        }
    }

    /**
     * Pulls the NTRIP sourcetable from the specified or current caster host:port.
     */
    fun fetchSourcetable(
        host: String? = null,
        port: Int? = null,
        user: String? = null,
        pass: String? = null
    ) {
        val targetHost = host?.takeIf { it.isNotBlank() } ?: _config.value.host
        val targetPort = port ?: _config.value.port
        val targetUser = user ?: _config.value.username
        val targetPass = pass ?: _config.value.password

        viewModelScope.launch {
            _isSourcetableLoading.value = true
            _sourcetableError.value = null
            val result = com.geodnet.ntrip.ntrip.NtripSourcetableClient.fetch(
                host = targetHost,
                port = targetPort,
                username = targetUser,
                password = targetPass
            )
            result.fold(
                onSuccess = { table ->
                    _sourcetable.value = table
                    _sourcetableError.value = null
                },
                onFailure = { err ->
                    _sourcetableError.value = err.message ?: "Failed to pull sourcetable"
                }
            )
            _isSourcetableLoading.value = false
        }
    }

    fun clearSourcetable() {
        _sourcetable.value = null
        _sourcetableError.value = null
    }

    override fun onCleared() {
        bleScanner.stopScan()
        bleReceiver.disconnect()
        bleReceiver.dispose()
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
