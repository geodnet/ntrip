Build an advanced, high-precision Android application designed for GNSS surveying, agricultural guidance, GIS mapping, and RTK navigation. It connects Android devices to external high-precision BLE RTK receivers, streams differential corrections from NTRIP Casters (such as GEODNET), injects mock location fixes into the Android operating system, forwards NMEA streams to GIS software like SW Maps over local TCP sockets, and logs raw binary & GNSS observations for post-processing (PPK).

## Implementation status

Every feature section below has an implementation, with two specific, deliberate exceptions kept
narrow and documented rather than silently skipped — see [`CLAUDE.md`](./CLAUDE.md)'s Status
section for exactly what and why:

1. RTCM 1230 (GLONASS code-phase biases) is recognized/counted but not detail-decoded — this
   codebase verifies RTCM bit-level decoders against independently-computed values before shipping
   them (see `CLAUDE.md`), and there was no way to do that for 1230 in this environment.
2. The Latency Engine's "20-bit MSM TOW modulo matching" isn't implemented as literally worded
   (real MSM TOW fields are ~30 bits); it correlates epochs by arrival-time clustering instead,
   which still produces the same Δt_epoch = t_last − t_first the spec asks for.

Also worth knowing: **nothing has been run on a real Android device or emulator** while building
this — none was available in the environment this was built in. Every piece of pure logic has real
unit tests, but the BLE/location/WebView/sensor/networking integration points all need a first
real-device smoke test before you trust them end-to-end.

One thing beyond this spec's original scope: section 2's "NTRIP Credentials Persistence" bullet
below described persisting *one* set of credentials. The app now manages a full list of named,
saveable connection profiles instead (add/load/update/delete) — see `CLAUDE.md`'s
`NtripProfile`/`NtripProfileRepository` entry.

- Building: `cd android && JAVA_HOME="<path to a JDK>" ./gradlew assembleDebug` (needs
  `local.properties`, not committed, with `sdk.dir` pointing at your Android SDK). Targets
  `compileSdk`/`targetSdk` 36, `minSdk` 26.
- See [`CLAUDE.md`](./CLAUDE.md) for what's actually implemented vs. planned, and architecture
  notes for what's built so far.

---

## 🚀 Key Features

### 📡 1. BLE RTK Receiver Integration & MTU Optimization
- **Nordic UART Service (NUS)**: Connects to external Bluetooth Low Energy GNSS receivers (u-blox, Unicore, Septentrio, etc.).
- **Optimized MTU (517 Bytes / 512B Payload)**: Automatically negotiates maximum BLE ATT MTU size and sets `CONNECTION_PRIORITY_HIGH` (11.25ms–15ms interval) for unfragmented, low-latency RTCM correction delivery.
- **Mixed Stream NMEA & RTCM3 Parser**: Continuously parses `$GNGGA`, `$GNRMC`, `$GNGST`, `$GNGSA` sentences AND binary `0xD3` RTCM3 frames (MSM, 1005 ARP, ephemerides) with CRC-24Q verification and dual message breakdown counters.
- **Bi-Directional Communication**: Streams differential RTCM 3.x correction frames directly to the receiver over BLE.

### 🌐 2. NTRIP 2.0 Caster Bridge & Sourcetable Browser
- High-performance NTRIP Client supporting CORS networks (Default: `rtk.geodnet.com:2101`).
- **GEODNET Regional Datum Resolver**: Automatically resolves dynamic geodetic datums from rover coordinates for `AUTO` and explicit mountpoints (`AUTO_ITRF2020`, `AUTO_WGS84`, `NATRF2020`, `SIRGAS2000`, etc.) per `geodnet.github.io/rtk`.
- **Live NTRIP Sourcetable Pulling**: Query and browse available mountpoints, formats (`RTCM 3.3`), constellations (`GPS+GLO+GAL+BDS`), and NMEA requirements directly from any caster.
- **NTRIP Profile Manager**: Manage, save, and switch named connection profiles across multiple casters.
- **Smart Phone Location Fallback**: Automatically initializes caster connection with phone GPS fix when no BLE RTK receiver is connected.
- **GNSS Ephemeris Filtering**: Default-enabled toggle to filter out heavy satellite ephemeris frames (`1019`, `1020`, `1041-1046`) before forwarding over BLE.

### 🛰️ 3. GEODNET Base Station Discovery & Active Station Matching
- **Proximity Discovery**: Discovers up to 20 active GEODNET base stations within a 100 km radius.
- **Active Base Station Matching**: Cross-matches RTCM 1005 ARP station IDs/coordinates and GGA differential station IDs to highlight the connected base station in green (`#10b981`), while rendering surrounding network base stations normally.
- **Real-Time Baseline & Azimuth**: Displays baseline distance, azimuth degrees, and cardinal bearings to each base station.

### ⏱️ 4. RTCM 3.x Inspector & Latency Engine
- Real-time decoding of RTCM 3.x message types:
  - **Base Station ARP**: `1005` / `1006`
  - **MSM Observation Frames**: `107X` (GPS), `108X` (GLONASS), `109X` (Galileo), `110X` (QZSS), `111X` (SBAS), `112X` (BeiDou), `113X` (NavIC) with compact log formatting
  - **Auxiliary Frames**: `1033` (Antenna/Receiver Descriptor), `1230` (GLONASS Code-Phase Biases), `1019-1046` (Satellite Ephemerides)
- **Epoch Latency Engine**: Calculates epoch duration, first message latency, last message latency, and differential age.

### 🗺️ 5. Offline Leaflet Map, Survey HUD & Coordinate Datum Transformation
- **Offline Leaflet Map**: Embedded satellite and street basemaps with persistent layer selection across sessions.
- **Tectonic Plate Coordinate Transformation (14-Parameter Time-Dependent Helmert)**: Ported from standard geodetic transformation engines (`coord.c`/`coord.h`). Automatically converts regional coordinates under `AUTO` (USA `NAD83(2011)/PA11/MA11`, Europe `ETRS89/ETRF2000`, Australia `GDA2020/GDA94`, New Zealand `NZGD2000`, South America `SIRGAS2000`, South Africa `ITRF1991`, Asia `ITRF2014/ITRF2008/CGCS2000/TUREF`) back to global **WGS84 / ITRF2020(2020.0)** for centimeter-accurate alignment on Google/ESRI/OSM tiles.
- **Interactive Gestures & Auto-Zoom**: Auto-zooms to rover on launch and smoothly pauses auto-zoom/following during manual pan/zoom gestures (with 1-tap re-center FAB).
- **Point-Based Trajectory Tracking**: Renders discrete survey track points color-coded by RTK fix quality (Green: RTK Fix, Amber: RTK Float, Blue: DGPS, Pink: Single).
- **Survey HUD Banner**: Displays real-time Baseline Length [km], Base Data Latency [s], Satellites, RMS horizontal/vertical accuracy, Base ID, and active datum transformation.
- **Static Segment Auto-Detection**: Automatically detects static survey stops using 2D horizontal clustering (RTK-fixed only, >5s duration, 5cm tolerance, transient float dropout tolerance). Computes 9-decimal centroid coordinates, 4-decimal height, and full 4-decimal NEU standard deviations ($\sigma_N, \sigma_E, \sigma_U, \sigma_{2D}, \sigma_{3D}$) logged to timestamped CSV files.

### 🔔 6. RTK Audio Beep Notifications
- **Surveyor Audio Cues**: Low-latency synthesized PCM tone alerts with anti-pop envelope ramps for key RTK state transitions:
  - **RTK First Fix**: Rising 3-tone chime ($1200\text{Hz} \to 1600\text{Hz} \to 2200\text{Hz}$).
  - **RTK Refix**: 2-tone rising chime ($1700\text{Hz} \to 2300\text{Hz}$).
  - **RTK Lost Fix**: Descending 2-tone warning ($950\text{Hz} \to 550\text{Hz}$).
  - **Entering RTK**: Ascending blip ($1100\text{Hz} \to 1450\text{Hz}$).
  - **Exiting RTK**: 2-buzz alarm ($420\text{Hz} \to 350\text{Hz}$).
- Runs continuously in the background foreground service even with the screen off or when surveying in external GIS apps.

### 📲 7. Android Mock Location & Local GIS TCP Servers
- **Android Mock Location Provider**: Injects high-precision RTK fixes directly into the Android OS (`LocationManager`) with full `Location.extras` metadata (raw `$GNGGA`/`$GPGGA` string, satellite count, fix quality, differential age, HDOP, station ID) for seamless compatibility with SW Maps, QField, and Lefebure.
- **NMEA TCP Server**: Broadcasts raw NMEA stream on port `10110` (e.g. for SW Maps native TCP Client mode).
- **RTCM TCP Server**: Broadcasts raw RTCM stream on port `10120`.

### 💾 8. Dual Data Logger (Raw Binary & Android GNSS Raw)
1. **Raw Binary Stream Logger**:
   - Structured `$GEOD,<timestampMs>,<payloadLength>,<binaryPayloadBytes>\r\n` framing for Base (`base.log`) and Rover (`rove.log`) streams.
   - Organized in GPS-time directories (`logs/yyyy-MM-dd/`).
2. **Android GNSS Raw Measurement Logger**:
   - Captures raw pseudoranges, carrier phase, Doppler, $C/N_0$, and navigation messages in standard RINEX/RTKLIB format (`*-raw-gnss.txt`).
