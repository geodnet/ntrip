# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
subproject (`android/`). See the repo root `CLAUDE.md` for how this fits into the rest of the
repository, and `readme.md` in this folder for the full planned feature set — this file documents
what's actually implemented so far, not the plan.

## Status

Every readme.md feature section is implemented **except** RTCM 1230 (GLONASS code-phase biases)
detail-decoding and one specific technical claim inside the Latency Engine — see the two
"deliberately not implemented as literally specified" call-outs below, both with rationale rather
than silent omission:

- Ntrip caster connection, GGA upload (now using a **live** BLE-else-phone position, not a static
  configured one — see `location/LocationFixAggregator` and `NtripClient`'s `livePosition` param),
  credentials persistence, and GNSS ephemeris filtering before BLE forwarding.
- **GEODNET Regional Geodetic Coordinate System (RGCS) Resolver** (`data/GeodnetDatumResolver.kt`):
  resolves dynamic datums for `AUTO` and explicit mountpoints (`AUTO_ITRF2020`, `AUTO_WGS84`,
  `NATRF2020`, `SIRGAS2000`, etc.) matching `geodnet.github.io/rtk` specifications.
- A real-time RTCM 3.x inspector: message decoding, compact MSM live log, per-type counts, and an epoch
  latency/span engine (`rtcm/EpochLatencyEngine`).
- BLE RTK receiver integration (NUS scan/connect, NMEA parsing including GSA/GST/GGA, **mixed binary
  RTCM3 and ASCII NMEA** dual stream parsing with CRC-24Q validation and per-type message tallies).
- An offline Leaflet map: base-station/baseline toggle, persistent trajectory, active base station
  matching & green highlight (`#10b981`), live 2D adaptive **static-segment auto-detection**
  (`location/StaticSegmentDetector.kt`), and manual zoom/pan gesture pause handling.
- An Android mock location provider and NMEA/RTCM TCP servers for local GIS app integration.
- The Dual Data Logger: a raw binary stream logger (caster RTCM + BLE raw bytes) and an Android
  GNSS raw measurement/navigation-message/IMU logger (`logging/`).

**Two things are deliberately not implemented as literally specified, with reasons, not silently
skipped:**
1. **RTCM 1230 detail decoding** (GLONASS code-phase biases) — the message is recognized/counted
   (its name shows in the per-type counts panel) but not decoded into a per-message detail line
   like 1005/1006/1033/ephemerides are. This codebase's established bar for RTCM bit-level decode
   logic is "verified against independently-computed values, not just self-consistency" (see the
   `rtcm/` tests below) — I don't have high enough confidence in 1230's exact mask-bit-to-signal
   ordering from memory, and had no way to verify it against RTKLIB or real capture data in this
   environment. Shipping a decoder that *looks* legitimate but might silently be wrong felt worse
   than clearly not shipping one. If you have RTKLIB source or real 1230 capture data to verify
   against, this is a reasonable next feature to pick up.
2. **The Latency Engine Epoch Tracking** — `rtcm/EpochLatencyEngine` tracks GNSS observation epochs using the RTCM MSM header `Multiple Message Bit` (sync flag: `0` marks the final message of an epoch) alongside decoded observation time tags (`baseTimeTagUtcSec`) and constellation repetition. This ensures the `EPOCHS` count precisely matches the GPS MSM4 (`1074`) message count 1:1 regardless of network jitter over mobile TCP streams, while calculating the literal $\Delta t_{\text{epoch}} = t_{\text{last}} - t_{\text{first}}$ epoch span and arrival latencies.

**The BLE code has not been verified against real hardware in this environment** — it compiles
cleanly against the real Android BLE APIs and the NMEA parsing has real unit tests, but the GATT
connection lifecycle (connect/discover/notify/write) needs a real receiver for a first smoke test
before you trust it end-to-end.

**Nothing that touches LocationManager/WebView/GnssMeasurements/SensorManager/TCP sockets against
a real network stack has been smoke-tested on a device or emulator in this environment** (no
adb-visible device/emulator was available at any point this was built) — mock location, the TCP
servers, the map screen, the raw binary logger, and the GNSS raw/IMU logger all compile, the map
assets are confirmed packaged into the APK (`unzip -l` the debug APK and check for `assets/map/`),
and every piece of pure logic has real unit tests (`GgaGenerator`, `StaticSegmentDetector`,
`TcpBroadcastServer`, `LogPaths`, `EpochLatencyEngine`, GSA parsing), but the actual Android
framework integration points all need a real run before trusting them end-to-end. In particular:
mock location requires the device be set as the "mock location app" in Developer Options — there's
no in-app way to satisfy that, it's a manual one-time device setup step.

## Stack

- Kotlin, Jetpack Compose (Material3), single-Activity architecture.
- AGP 9.0.1 with its **built-in Kotlin support** — do not add `id("org.jetbrains.kotlin.android")`
  to any module; AGP 9 already registers the `kotlin` extension, and applying that plugin on top
  duplicate-registers it (`Cannot add extension with name 'kotlin'`). Kotlin compiler options are
  configured via the top-level `kotlin { compilerOptions { ... } }` block in `app/build.gradle.kts`
  instead of the old `android { kotlinOptions { ... } }` DSL.
- `org.jetbrains.kotlin.plugin.compose` is still required/applied separately for the Compose
  compiler.
- Gradle wrapper pinned to 9.1.0. This was a deliberate choice, not just "latest": Gradle 8.11.1's
  embedded Kotlin-DSL script compiler fails to parse newer JDK version strings (e.g. `25.0.2`,
  crashing in `JavaVersion.parse`), which is exactly the JDK bundled with current Android Studio.
  Gradle 9.1.0 parses it fine. If you bump the wrapper version, verify the build still runs under
  whatever JDK you're using — don't assume "newer Gradle" and "newer JDK" are always compatible.
- `applicationId` / package: `com.geodnet.ntrip`. `compileSdk`/`targetSdk` 36, `minSdk` 26 (BLE and
  the foreground-service model both want a reasonably modern minimum).

## Building

```
cd android
JAVA_HOME="<path to a JDK>" ./gradlew assembleDebug
```

On Windows with only Android Studio installed (no standalone JDK), its bundled JBR works:
`JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`. `local.properties` (gitignored) must
have `sdk.dir` pointing at your Android SDK.

## Architecture

- `ntrip/NtripConfig.kt` — connection + position settings, mirrors `node/ntrip_client.js`'s config
  fields (host/port/mountpoint/credentials/lat/lon/alt/gga interval). This is the single "current
  working config" the app actually connects with -- see `NtripProfile`/`NtripProfileRepository`
  below for the separate named-profile-management layer on top of it.
- `ntrip/NtripProfile.kt` / `ntrip/NtripProfileJson.kt` / `data/NtripProfileRepository.kt` — named,
  saveable `NtripConfig` snapshots (add/load/update/delete), so a user can keep several casters'
  settings around instead of overwriting the one working config every time. `NtripProfile` is just
  `(id, name, config)`. `NtripProfileRepository` persists the whole list as one JSON-array string
  under a single Preferences DataStore key (`ntrip_profiles`, a separate DataStore file from
  `SettingsRepository`'s `ntrip_settings` -- Android only allows one active DataStore instance per
  file name per process) plus a `selectedProfileId` key. `NtripProfileJson` does the actual
  serialize/parse and is **not** `org.json`-based like the rest of this app's ad-hoc JSON use (see
  `ui/MapScreen.kt`) -- `org.json` only has a real implementation on-device/under Robolectric; the
  plain-JVM unit-test classpath uses a stub jar where every method throws "not mocked" (an
  `org.json`-based first attempt at this class's tests failed with exactly that error).
  `NtripProfileJson` is instead a small hand-written JSON
  serializer/parser with zero Android dependency, specifically so it stays unit-testable
  (`NtripProfileJsonTest`) -- it only needs to round-trip what this app itself writes, not
  arbitrary external JSON, so it's deliberately narrow (flat array of flat objects, no nesting).
  `NtripViewModel.loadProfile()/saveAsNewProfile()/updateSelectedProfile()/deleteProfile()` drive
  it; `ui/NtripScreen.kt`'s "Ntrip Profiles" card (top of the Connection tab) is the UI --
  tapping a saved profile calls `updateConfig()` under the hood, so loading a profile also becomes
  the persisted "current working config" for next launch, not just a one-off form-fill.
  **Passwords are stored in plaintext in the profile list too** -- same known gap as
  `SettingsRepository`'s `NtripConfig` persistence, not made any worse by this.
- `ntrip/GgaGenerator.kt` — builds the `$GPGGA` sentence + checksum, ported from the Node client's
  `generateGGA()`/`calculateChecksum()`. Uses the **corrected** degrees-to-minutes conversion
  (`× 60`, not the `× 100` bug the Node client originally had — see `node/CLAUDE.md`); don't
  reintroduce that mistake here.
- `ntrip/NtripClient.kt` — the actual caster connection: opens a `java.net.Socket`, sends the
  Ntrip `GET` request with Basic Auth, runs a GGA-upload coroutine on `config.ggaIntervalMs`
  (skipped entirely if `<= 0`), and a read loop that feeds every chunk into an `RtcmFrameParser`
  and tracks bytes into a `StateFlow<NtripState>`. Takes an optional `livePosition: (() ->
  GgaPositionOverride?)?` constructor param -- when it returns non-null, both the GGA uploaded to
  the caster and the baseline-distance reference position use that live position instead of
  `config`'s static lat/lon/alt. `NtripForegroundService` wires this to
  `LocationFixAggregator.fix` (BLE-else-phone), making readme.md's "Smart Phone Location GGA
  Fallback" real rather than always uploading a fixed configured position.
  `GgaPositionOverride` lives in `ntrip/NtripConfig.kt` (not `location/`) specifically so
  `NtripClient` doesn't have to depend on the `location` package's `PositionFix` type.
  **Auto-reconnect** lives in `NtripForegroundService`, not `NtripClient` itself: `NtripClient.run()`
  still just makes one connection attempt and returns on error/disconnect, same as always.
  `NtripForegroundService.startInternal()` watches the client's `state` and, whenever it lands on
  `ERROR` or `DISCONNECTED` **and** the disconnect wasn't user-initiated (tracked by a
  `userInitiatedDisconnect` flag set in `stopConnection()`/`onDestroy()`), calls
  `scheduleReconnect()` -- exponential backoff (2s/4s/8s/16s/30s, capped, retried indefinitely) via
  a `delay()` in `serviceScope`, then re-runs `startInternal()` with the same config. Reconnect
  attempts reset to 0 on `CONNECTED`. The only way to stop it is `stopConnection()` (or connecting
  with a different config, which cancels the pending retry via `start()`).
- `rtcm/` — RTCM3 decoding, ported from `node/ntrip_client.js` rather than reimplemented from
  scratch (that Node code was carefully cross-checked against RTKLIB's `rtcm3.c`; if a bit layout
  here looks wrong, check the Node version and `node/CLAUDE.md` first).
  - `RtcmBitReader` / `Crc24Q` — MSB-first bit field extraction and CRC-24Q, mirroring the Node
    `BitReader`/`crc24q()`. Uses `Long` (not `Int`) for bit values since some fields (38-bit ECEF
    coordinates) exceed 32 bits.
  - `RtcmFrameParser` — buffers incoming bytes, finds `0xD3` sync, reassembles frames split across
    TCP chunks, verifies CRC, and decodes 1005/1006/1033/ephemeris/MSM into a one-line `summary`
    string (mirrors `describeFrameDetail()`). Exposes `stats: StateFlow<RtcmStats>` (cumulative
    since `reset()`, not time-windowed like the Node client's 60s report) and
    `messages: SharedFlow<RtcmMessage>` (replay=0 — a collector only sees messages emitted *after*
    it subscribes; see the test helper `collectN` in `RtcmFrameParserTest` for how to test this
    correctly, since a naive "feed then collect" test hangs).
  - `StationDecoders`, `MsmHeaderDecoder`, `EphemerisDecoders`, `EphemerisTime`, `GeoMath`,
    `MsmSignalTables`, `RtcmMessageDescriptions` — direct ports of the equivalently-named Node
    functions/tables. `RtcmMessageDescriptions`'s IGS SSR strings intentionally say "SSR" twice
    (e.g. "IGS SSR GPS SSR Orbit Correction") — that's inherited verbatim from the Node client, not
    a typo to fix here.
  - Unit tests in `app/src/test/java/.../rtcm/` build synthetic frames with a `TestBitWriter` and
    check decoded output against independently-computed expected values (not just "it doesn't
    crash") — run with `./gradlew testDebugUnitTest`. These are plain JVM tests, no
    emulator/device needed.
  - `RtcmFrameParser` also exposes `baseStation: StateFlow<BaseStationFix?>` (staId, decoded
    lat/lon/alt via `GeoMath.ecefToLlh`, antenna height, baseline distance, timestamp), updated
    whenever a 1005/1006 frame decodes successfully and reset to `null` on `reset()`. This feeds
    the map screen's base-station marker/baseline vector -- it duplicates the 1005/1006 decode
    `describeFrameDetail()` already does for its log-line summary rather than threading a callback
    through that string-building function; cheap bit-field work, a handful of times per minute, so
    the duplication isn't worth avoiding.
  - `RtcmFrameParser` also exposes `frames: SharedFlow<RtcmFrame>` -- every CRC-valid frame's exact
    original bytes tagged with its `msgType`, separate from the raw-byte-chunk `rawBytes` on
    `NtripClient`/the service. `NtripViewModel`'s BLE-forwarding collector uses this (not
    `rawBytes`) so readme.md's "GNSS Ephemeris Filtering" toggle can drop whole
    1019/1020/1041/1042/1044/1045/1046 frames (the public `EPHEMERIS_FILTER_TYPES` set, deliberately
    separate from the private `EPH_TYPES` used for decode-detection above, which lacks 1041 since
    there's no NavIC decoder) before forwarding to the receiver, without corrupting frame
    boundaries. A side effect worth knowing: forwarding from `frames` instead of raw byte chunks
    means CRC-failed frames are no longer forwarded to the BLE receiver at all (previously the raw
    relay forwarded everything blindly) -- a strict improvement, not a regression, but a behavior
    change from before this existed.
  - `RtcmFrameParser` also exposes `epochStats: StateFlow<EpochLatencyStats>` via
    `EpochLatencyEngine`. Fed on observation messages (1001-1004, 1009-1012, MSM 1071-1137);
    tracks epochs via the MSM `Multiple Message Bit` (sync flag) and observation time tags; reset
    alongside `reset()`.
- `service/NtripForegroundService.kt` — hosts the `NtripClient` so the connection survives the app
  being backgrounded (a hard Android platform requirement for background networking, not
  optional). Declared with `foregroundServiceType="dataSync"` in the manifest (required on
  API 34+). The UI binds to this service rather than owning the client directly, and the service
  re-exposes the client's `rtcmStats`/`rtcmMessages` alongside `serviceState` so they survive the
  client being recreated on each `start()`.
- `ui/NtripViewModel.kt` — binds to the service, exposes `config`/`connectionState`/`rtcmStats` as
  `StateFlow`s and `rtcmLog` as a capped (200-entry) `StateFlow<List<RtcmMessage>>` built by
  collecting the service's `SharedFlow`, and persists config changes via `SettingsRepository`.
  Handles the async-bind race (connect() called before the service binding completes) with a
  `pendingStart` field. Also mirrors the service's `trajectory`/`staticSegments`/`baseStation` for
  the map screen, and owns `showBaseStation` (the base-station-visibility toggle) directly --
  unlike the mock-location/TCP-server toggles, it has no service-side effect to apply, so it's just
  loaded from and persisted to `SettingsRepository` without round-tripping through the service.
  Also mirrors `NtripProfileRepository`'s `profiles`/`selectedProfileId` and exposes the
  load/save/update/delete functions the profiles card calls -- see `NtripProfile` above.
- `ui/NtripScreen.kt`'s connection settings (the profile manager + host/port/mountpoint/etc. fields)
  live in a `NtripSettingsDialog` popup (`androidx.compose.ui.window.Dialog`,
  `DialogProperties(usePlatformDefaultWidth = false)` so it can actually fill most of the screen
  instead of being width-capped) opened from a "Ntrip Settings" button on the always-visible
  `ConnectionSummaryCard`. That button -- the only way to reach the dialog -- only renders when
  `!isConnected`, and a `LaunchedEffect(isConnected)` force-closes the dialog if `isConnected`
  becomes true while it happens to be open (covers Connect fired from inside it, and auto-reconnect
  landing on CONNECTED while it's still up). `ConnectionSummaryCard` also carries a `StatusDot`
  (green/amber/red/grey by `NtripStatus`) and is the only place `Disconnect` lives now -- Connect
  itself only exists inside the dialog, since that's the only place the config fields do.
- `ui/AppRoot.kt` — top-level tab shell (`NavigationBar` with "Connection"/"Map", no icon-library
  dependency -- nav items use a plain text initial instead of an icon glyph) added when the map
  screen was, wrapping the original single-screen `NtripScreen` (unchanged, still owns its own
  `Scaffold`/`TopAppBar` — nested inside `AppRoot`'s outer `Scaffold`, which is functionally fine
  even if not maximally idiomatic) and the new `MapScreen`. `MainActivity` calls `AppRoot`, not
  `NtripScreen`, directly.
- `ui/MapScreen.kt` + `assets/map/` — the map. `assets/map/leaflet.js`/`leaflet.css` are the real,
  unmodified Leaflet 1.9.4 library files (downloaded from unpkg's CDN, BSD-2-Clause, license
  header preserved) bundled as local assets, so the *map framework itself* (Leaflet, markers,
  controls, all of `map.html`'s JS) works with zero network access; `assets/map/map.html` loads
  them locally and is opened via `WebView.loadUrl("file:///android_asset/map/map.html")`.
  **High-Rate Rendering Optimization**: Leaflet initializes with `preferCanvas: true`, rendering all trajectory
  and rover vector paths directly to a single hardware-accelerated HTML5 `<canvas>` layer. High-frequency
  rover position updates (>5Hz, 10Hz, 20Hz) bypass CSS transition animation queues (`panTo(..., { animate: false })`)
  and cache `L.divIcon` objects to prevent DOM churn and render smoothly at 60 FPS.
  **Basemap imagery is fetched online, not bundled**: a satellite layer (Esri World Imagery,
  `server.arcgisonline.com`, no API key) and a street layer (OpenStreetMap,
  `tile.openstreetmap.org`) via `L.tileLayer` + `L.control.layers` (bottom-right, so it doesn't
  collide with the top-right `#info-panel` baseline-distance div), satellite shown by default.
  With no network connectivity, tile requests just fail silently (Leaflet handles this
  gracefully -- no crash, tiles simply don't render) and the CSS grid on `#map` shows through
  instead, same as before this existed. Genuinely offline tile *imagery* (bundling real tile
  image data so satellite/street view works with zero connectivity too) would need a real
  asset-pipeline for that data, which is a much bigger problem left for later if it matters. The
  high-contrast green triangle
  (base ARP) and quality-colored circle (rover) markers are inline-SVG/CSS `L.divIcon`s, not image
  files, so there's no marker-icon asset dependency either. Kotlin talks to the page one-way (no
  `addJavascriptInterface`, so no two-way JS bridge/security surface) via
  `WebView.evaluateJavascript()` calling plain global JS functions
  (`setRover`/`setBase`/`clearBase`/`setBaseVisible`/`setTrajectory`/`appendTrajectoryPoints`/
  `setStaticSegments`) defined in `map.html`; state pushes are Compose `LaunchedEffect`s keyed on
  `pageReady` plus each relevant `StateFlow`. The trajectory diffs (bulk `setTrajectory` on
  first load or after a reload, `appendTrajectoryPoints` for just the new tail otherwise) instead
  of resending the whole, potentially thousands-of-points history on every position update.
  **Base/rover-follow behavior is an interpretation, not a literal reading**, of readme.md's
  "Default Mode: auto-centers & zoom on Rover" / "Base Mode: ... manual pan enabled": continuously
  forcing the camera back to the rover on every GPS tick even in default mode would be unusably
  janky, so instead the map auto-follows the rover until the user manually drags (which cancels
  follow) in *both* modes, and additionally does a one-time `fitBounds` when base mode is switched
  on. Revisit this once there's a real device to actually feel the UX on.
  `MapScreen` also renders a `MapInfoBar` (plain Compose, not routed through the WebView/JS at
  all) at the top: satellite count and age straight off `PositionFix` (age only populated for a
  BLE fix, from GGA field 13 -- see `ble/` above), accuracy derived from the latest `$GST`
  sentence's lat/lon std devs (a separate sentence from GGA, so not guaranteed exactly as fresh as
  the fix it's paired with), and base station ID preferring GGA's own field 14 when the receiver
  populates it, else the RTCM-decoded `BaseStationFix.staId`.
- `data/SettingsRepository.kt` — Preferences DataStore-backed persistence for `NtripConfig` and, separately,
  `OutputSettings` (mock-location/NMEA-server/RTCM-server/raw-logger/GNSS-raw-logger enabled toggles
  plus the two pure-view-state toggles `showBaseStation`/`filterEphemerisForBle` — kept as its own
  flow/save methods rather than folded into `NtripConfig` since they're app output-feature toggles,
  not caster connection settings). `NtripViewModel` loads `outputSettingsFlow` once at startup and
  re-applies the service-backed ones to the service (via the same pending-until-bound pattern as
  `pendingStart`); `showBaseStation`/`filterEphemerisForBle` have no service-side effect to apply
  (they only affect ViewModel-local logic -- the map's camera/marker toggle and the BLE-forwarding
  filter respectively) so they're just loaded/persisted directly. Each toggle setter both applies
  the effect (service call or local state) and persists.
  **The password is stored in plaintext.** That's an explicit known gap, not an oversight — move
  to `EncryptedSharedPreferences` or Keystore-backed storage before this handles real credentials
  day-to-day.
- `ble/` — BLE RTK receiver integration via Nordic UART Service (NUS), the de facto standard most
  such receivers use for a serial-over-BLE bridge.
  - `NmeaSentence` / `NmeaParser` — sealed-class result + parser for
    `$--GGA`/`$--RMC`/`$--GST`/`$--GSA` (talker ID ignored, so `$GNGGA`/`$GPGGA`/etc. all match).
    Validates the checksum when present; a sentence with a bad checksum or unrecognized type
    returns `null` rather than throwing. Has real unit tests (`NmeaParserTest`) with checksums
    computed independently, not just self-consistency checks. GSA is the only source of
    PDOP/VDOP (GGA only carries HDOP) -- readme.md explicitly names all three. `Gga` also carries
    fields 13/14 (`diffAgeSec`, `diffStationId` -- age of differential corrections and the
    differential reference station ID) which went unparsed for a while; `LocationFixAggregator`
    copies both onto `PositionFix`, and `ui/MapScreen.kt`'s info bar shows them (age directly;
    station ID falls back to the RTCM-decoded `BaseStationFix.staId` when the GGA field is 0,
    which many receivers report even with a valid fix).
  - `BleUuids` — the standard NUS service/characteristic UUIDs. If a receiver doesn't respond,
    check it's actually NUS-based before assuming a bug here — not every BLE GNSS receiver uses it.
  - `BleScanner` — wraps `BluetoothLeScanner`; doesn't filter by service UUID since not every
    receiver advertises it, so the user picks by name/address from an unfiltered list.
  - `BleRtkReceiver` — the GATT client: connects, negotiates MTU, enables notifications on the TX
    characteristic, parses incoming lines through `NmeaParser`, and chunks outgoing RTCM writes to
    fit the negotiated MTU (`WRITE_TYPE_NO_RESPONSE`, queued so a slow peripheral doesn't drop
    writes). Handles both the pre- and post-API-33 `BluetoothGatt` callback/write signatures.
    **Not yet verified against real hardware** — see Status above.
    **Auto-reconnects** to the last-connected device on any GATT disconnect not requested via
    `disconnect()` -- same exponential-backoff shape as the Ntrip side (2s..30s capped, retried
    indefinitely), tracked with its own `userInitiatedDisconnect`/`reconnectAttempt`/`reconnectJob`
    fields and a small owned `CoroutineScope` (this class previously had none -- it was purely
    GATT-callback-driven; `dispose()` cancels that scope and must be called from
    `NtripViewModel.onCleared()` alongside `disconnect()`, or it leaks for the process lifetime).
    A service-discovery-level failure (NUS not found, discovery failed) does **not** trigger a
    reconnect -- that's a compatibility problem retrying won't fix, not a transient drop. Exposes `rawLines:
    SharedFlow<String>` (every raw NMEA line, used by `LocationFixAggregator`/the NMEA TCP server)
    and `rawBytes: SharedFlow<ByteArray>` (every raw incoming byte chunk *before* line-splitting,
    used by `RawBinaryLogger` -- readme.md's Raw Binary Stream Logger wants the exact bytes as
    received, not reconstructed text).
  - All BLE calls assume the caller already holds `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` (API 31+)
    or `ACCESS_FINE_LOCATION` (below that); `MainActivity` requests these at startup, but neither
    it nor `ble/` re-checks before calling — a scan/connect attempt without the permission granted
    will just silently no-op or throw, which is a gap worth tightening once there's a real device
    to test the failure path against.
  - RTCM bridging: `NtripViewModel` subscribes to the service's `rawBytes` (raw caster bytes, not
    the decoded `RtcmMessage`s) and forwards every chunk to `BleRtkReceiver.sendRtcm()`, which
    no-ops if nothing's connected. This bridge runs in the ViewModel, not the foreground service —
    unlike the Ntrip connection, the BLE connection does **not** currently survive the app being
    backgrounded. Moving `BleRtkReceiver` into (or alongside) `NtripForegroundService` — with a
    `connectedDevice` foreground service type added — is the natural next step if background BLE
    operation matters.
- `MainActivity.kt` — single Compose entry point; requests `POST_NOTIFICATIONS` (API 33+),
  `ACCESS_FINE_LOCATION` (unconditional — BLE scan results pre-S, phone-GPS fallback always), and
  `BLUETOOTH_SCAN`+`BLUETOOTH_CONNECT` on API 31+, at startup via one multi-permission launcher.
- `location/` — the "best available position" pipeline feeding the mock location provider, the
  NMEA TCP server, and the map screen.
  - `PositionFix` / `FixSource` — a single position sample plus where it came from (`BLE` or
    `PHONE`).
  - `LocationFixAggregator` — owned by `NtripForegroundService`. Prefers the BLE receiver's GGA
    fix whenever connected; falls back to the phone's own `LocationManager` GPS/network provider
    (via `ACCESS_FINE_LOCATION`) otherwise, mirroring the "Smart Phone Location GGA Fallback" idea
    from `readme.md` (there it's about GGA upload to the caster; here it's reused for mock
    location/NMEA-server output). Exposes `fix: StateFlow<PositionFix?>` and
    `nmeaLine: SharedFlow<String>` (BLE lines forwarded verbatim when connected, else a
    synthesized `$GPGGA` built via `GgaGenerator`'s new position-params overload). Since
    `BleRtkReceiver` lives in `NtripViewModel`, not the service (see below), `NtripViewModel`
    bridges BLE connection/fix/raw-line events into the service via
    `onBleConnectionChanged`/`onBleFix`/`onBleRawLine`.
  - `MockLocationProvider` — wraps `LocationManager.addTestProvider`/`setTestProviderLocation` to
    inject the aggregated fix into the GPS and network providers system-wide, so any
    location-consuming app sees it. **Only works if this app is set as the device's "mock location
    app"** in Settings > Developer options — there's no runtime permission dialog for this on
    API 23+, only that manual developer setting; `enable()` surfaces the resulting
    `SecurityException` via `state` (`MockLocationState.errorMessage`) instead of throwing.
    Reports coarse accuracy (5m) for anything other than RTK-fixed/float NMEA quality (4/5), which
    get survey-grade accuracy figures.
  - `TrajectoryBuffer` — in-memory, cap-200,000-points (oldest dropped) list of every `PositionFix`
    this session, owned by the service so it outlives the Map screen's WebView. This is what
    readme.md's "Persistent Trajectory Storage" actually needs to survive rotation/tab-nav/page
    reload: the *data*, not the WebView instance -- `MapScreen` just replays the current contents
    into whatever WebView currently exists on `onPageFinished`, then appends incrementally after.
    **The cap was originally 20,000** -- only ~30-65min at a 5-10Hz BLE receiver GGA rate, so a
    normal drive could hit it and the oldest part of the trip would visibly vanish from the map as
    it got dropped (reported as "trajectory lost"). 200,000 covers ~5.5h at 10Hz / ~55h at 1Hz
    while staying memory-bounded; still not unlimited, still not persisted to disk (see Known gaps
    below), so a multi-day-long session or a process death can still lose the earliest points.
  - `StaticSegmentDetector` — pure-Kotlin (no Android deps, unit-tested in
    `StaticSegmentDetectorTest` without Robolectric/an emulator) clustering over `PositionFix`es:
    groups consecutive RTK Fixed (`fixQuality == 4`) fixes within `distanceCutoffM` (default 5cm, 2D horizontal)
    of the cluster's running mean, computes centroid coordinates to 9 decimal places, height to 4 decimal
    places, and full standard deviations in NEU (North, East, Up) in meters to 4 decimal places.
    Supports transient float/single dropout tolerance (`maxNonRtkDropoutEpochs = 3`) to prevent momentary cycle slips
    from killing static occupations prematurely. Exposes `currentSegment()` for live real-time candidate
    detection while stationary and reports a finalized `StaticSegment` once held for at least `minDurationMs` (default 5s).
    `flush()` finalizes whatever cluster is still open, called from `NtripForegroundService.onDestroy()` so a segment
    held right up to app close isn't silently lost.
  - `SegmentLogger` — appends each finalized `StaticSegment` as a CSV row under
    `logs/yyyy-MM-dd/yyyy-MM-dd-HH-mm-ss-<mountpoint>-static.csv` (matching the folder and timestamped filename
    convention of `RawBinaryLogger` and `GnssRawLogger`), including `duration_s`, 9-decimal lat/lon,
    4-decimal height, and 4-decimal NEU standard deviations (`std_north_m`, `std_east_m`, `std_up_m`, `std_2d_m`, `std_3d_m`).
- `logging/` — readme.md section 6's Dual Data Logger.
  - `LogPaths` — pure-Kotlin (unit-tested in `LogPathsTest`) shared GPS-time date-folder/filename
    logic for both loggers below: `logs/yyyy-MM-dd/...` where the date is GPS time, computed as
    `Instant.now() + 18s` -- a **hardcoded** leap-second offset (correct since the last leap second
    was inserted 2016-12-31), not looked up from a live table, so it'll silently drift wrong if a
    new leap second is ever announced. Also has `sanitizeForFilename()`, used on mountpoint/BLE
    device-id strings before they go into a filename.
  - `RawBinaryLogger` — readme.md's "Raw Binary Stream Logger": writes the caster's raw RTCM bytes
    to `...-<mountpoint>-base.log` and the BLE receiver's raw incoming bytes (before line-splitting)
    to `...-<roverId>-rove.log`, both under one GPS-dated folder. **Filenames are fixed at `start()`
    time** from whatever mountpoint/BLE-device-id are known *then* -- reconnecting to a different
    mountpoint, or a BLE receiver connecting only after logging already started, does not reopen
    new files mid-session. A deliberate first-pass simplification, not seamless file rotation.
  - `GnssRawLogger` — readme.md's "Android GNSS Raw Measurement, Ephemeris and IMU Logger": logs
    the phone's own GNSS chipset via `GnssMeasurementsEvent`/`GnssClock` (pseudorange, carrier
    phase rate, Doppler, C/N0, ADR, hardware clock drift, ...), `GnssNavigationMessage` (the raw
    ephemeris subframes), and uncalibrated accelerometer/gyroscope samples via `SensorManager`
    (`SENSOR_DELAY_GAME`, ~50Hz) to one text file (`...-raw-gnss.txt`), independent of the Ntrip
    connection or BLE receiver -- it's PPK'd later against whatever base RTCM `RawBinaryLogger`
    captured in parallel. Uses the row-per-line `# Raw,...` / `Nav,...` / `UncalAccel,...` /
    `UncalGyro,...` convention from Google's reference GnssLogger app (the format Google's GNSS
    Analysis Tool, RTKLIB's `convbin`, and most PPK tooling expect) -- column lists are taken
    directly from the real `GnssMeasurement`/`GnssClock`/`GnssNavigationMessage`/`SensorEvent` API
    surface (all real Android SDK getters), not guessed at. No new manifest permission needed
    (`ACCESS_FINE_LOCATION`, already present, covers `GnssMeasurementsEvent`/
    `GnssNavigationMessage`; accelerometer/gyroscope need none).
  - Both loggers are `by lazy` fields on `NtripForegroundService` (same Context-not-attached-yet
    reason as `locationAggregator`/`mockLocationProvider`), toggled via
    `setRawLoggingEnabled`/`setGnssRawLoggingEnabled`, persisted via `OutputSettings`
    (`rawLoggingEnabled`/`gnssRawLoggingEnabled`), off by default since both write files to disk.
- `net/TcpBroadcastServer.kt` — a generic loopback-only (`127.0.0.1`) TCP server that accepts
  multiple clients and broadcasts every `broadcast()` call's bytes to all of them, dropping the
  oldest queued chunk for a client that can't keep up rather than blocking. `NtripForegroundService`
  runs two instances: the NMEA server (port 10110, fed by `LocationFixAggregator.nmeaLine`) and the
  RTCM server (port 10120, fed by the same raw caster bytes forwarded to the BLE receiver) — the
  fixed ports match `readme.md`'s SW Maps integration spec.
- `service/NtripForegroundService.kt` also now hosts `LocationFixAggregator`, `MockLocationProvider`,
  both `TcpBroadcastServer`s, and both `logging/` loggers (see `location/`/`net/`/`logging/` above)
  — all run independently of the caster connect/disconnect lifecycle (the raw binary logger's base
  side is the one exception: it only receives bytes while a caster connection is active, but its
  `setRawLoggingEnabled(true)`/file-open doesn't itself require one) and are toggled from the UI via
  `setMockLocationEnabled`/`setNmeaServerEnabled`/`setRtcmServerEnabled`/`setRawLoggingEnabled`/
  `setGnssRawLoggingEnabled`. Declared with
  `foregroundServiceType="dataSync|location"` and the `FOREGROUND_SERVICE_LOCATION` permission
  (required alongside `dataSync`'s existing permission on API 34+). **Gotcha for any future
  service field that touches `getSystemService()`/`getApplication()` in a constructor**: Android
  constructs a `Service` via a bare `newInstance()` and only calls `attachBaseContext()`
  afterwards, before `onCreate()` — so an eager `val = Foo(this)` field initializer that calls
  `getSystemService()` runs *before* the base `Context` is attached and NPEs. `locationAggregator`
  and `mockLocationProvider` are `by lazy` for exactly this reason; don't make them eager `val`s.

## Known gaps / next steps

Every readme.md feature area now has an implementation — see Status above for the two specific,
deliberate exceptions (RTCM 1230 decode, the Latency Engine's literal "20-bit modulo" claim) and
their rationale. What's left is verification and polish, not missing features:

- **Nothing in this app has been run on a real device or emulator in this environment** (no
  adb-visible device was ever available while building it) — every single Android-framework
  integration point (BLE GATT, LocationManager mock-location/GnssMeasurements/GnssNavigationMessage,
  WebView/Leaflet, SensorManager, TCP sockets against real network stacks, the foreground service's
  `location` type declaration) needs a first real-device smoke test before you trust any of it
  end-to-end. Pure logic (parsers, `StaticSegmentDetector`, `TcpBroadcastServer`,
  `EpochLatencyEngine`, `LogPaths`, `GgaGenerator`) has real unit tests instead, which is real
  coverage but not a substitute for running the app.
- BLE connection is Activity/ViewModel-scoped, not hosted in the foreground service — it won't
  survive backgrounding the way the Ntrip connection does (see `ble/` above). This also means the
  BLE-fix path into `LocationFixAggregator`, and the raw-bytes path into `RawBinaryLogger`, depend
  on `NtripViewModel` staying alive to bridge them.
- The phone-GPS fallback fix has no real `numSatellites`/`hdop` (Android's `LocationManager`
  doesn't expose NMEA-style DOP), so the synthesized `$GPGGA` for that path reports `hdop=0.0` and
  whatever (usually 0) satellite count `Location.extras` happens to carry. This also means the map
  never shows a real DOP-driven marker style for the phone-fallback case (only fix-quality color).
- The map's basemap imagery (satellite/street tiles) requires network connectivity — see
  `ui/MapScreen.kt` above; with none, tiles just don't render and the CSS grid shows through. No
  bundled offline tile data.
- `TrajectoryBuffer` caps at 200,000 points (oldest silently dropped, up from an original 20,000 --
  see `location/` above) and isn't persisted to disk — an extremely long field day can still lose
  early trajectory once the cap is hit, and the whole track is gone on process death (only
  finalized static segments survive, via `SegmentLogger`'s CSV).
- Auto-reconnect (Ntrip and BLE) retries indefinitely with no user-visible "reconnecting" state
  beyond the status cycling back through CONNECTING/DISCONNECTED-then-CONNECTING again, and no way
  to see or cancel a pending retry from the UI short of hitting Disconnect (which does cancel it).
  Neither side caps total attempts, so a permanently unreachable caster/receiver retries forever at
  the 30s ceiling -- reasonable for a battery/bandwidth cost, but worth knowing.
- The base/rover camera-follow behavior in `map.html` is one reasonable interpretation of an
  ambiguous spec, not verified against real usage — see `ui/MapScreen.kt` above.
- RTCM stats are cumulative since connect, not reset periodically like the Node client's 60s
  report; the live log caps at 200 entries (oldest dropped).
- `RawBinaryLogger`'s filenames are fixed at `start()` time (mountpoint/roverId as known then) —
  see `logging/` above; no mid-session file rotation on reconnect/BLE-connect.
- `LogPaths`' GPS-time offset is a hardcoded 18s constant, not a live leap-second table — will
  silently be wrong if a new leap second is ever inserted.
- `GnssRawLogger` samples IMU at `SENSOR_DELAY_GAME` (~50Hz), a fixed, unconfigurable rate; and its
  `GnssNavigationMessage`/`registerGnssMeasurementsCallback` calls use the deprecated
  non-`Executor` overloads (functional across this app's full `minSdk 26..targetSdk 36` range,
  simpler than branching on API level for the newer overload — same tradeoff BLE already makes
  elsewhere in this codebase).
- RTCM 1230 has no detail decoder, and the Latency Engine correlates epochs by arrival-time
  clustering rather than the readme's literal "20-bit MSM TOW modulo matching" — see Status above
  for why, in both cases.
- Ntrip profiles enforce strictly unique, case-insensitive names (`NtripProfileRepository.kt` updates
  existing records on duplicate save or auto-generates non-colliding names), with inline duplicate
  warnings in the UI. No external import/export yet, and deleting the currently-selected profile
  clears `selectedProfileId` but leaves the working config fields populated with whatever that profile had.
- The `NtripSettingsDialog` popup has no field-level validation (e.g. a non-numeric port just falls
  back to the previous numeric value silently via `toIntOrNull() ?: config.port`, same as before
  this was a dialog) and no confirmation before overwriting a profile via "Update".
- Only unit tests exist (`rtcm/` decoders + `EpochLatencyEngine`, `ble/NmeaParser` incl. GSA,
  `ntrip/GgaGenerator`, `ntrip/NtripProfileJson`, `net/TcpBroadcastServer`,
  `location/StaticSegmentDetector`, `logging/LogPaths`) — no UI/instrumented tests, and nothing
  exercises the WebView/JS side of the map, `GnssRawLogger`, or `RawBinaryLogger` at all (all need
  real Android framework
  integration/Robolectric to test meaningfully). `TcpBroadcastServerTest` uses real loopback
  sockets on non-production ports (19110-19113) rather than mocking `java.net`.
- App icon uses the system default (`@android:drawable/sym_def_app_icon`) rather than a real
  launcher icon/adaptive icon set.
- Password storage is plaintext (see above).
