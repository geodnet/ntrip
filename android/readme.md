Build an advanced, high-precision Android application designed for GNSS surveying, agricultural guidance, GIS mapping, and RTK navigation. It connects Android devices to external high-precision BLE RTK receivers, streams differential corrections from NTRIP Casters (such as GEODNET), injects mock location fixes into the Android operating system, forwards NMEA streams to GIS software like SW Maps over local TCP sockets, and logs raw binary & GNSS observations for post-processing (PPK).

## Implementation status

This is the product spec / feature vision. Actual implementation is early-stage: Ntrip caster
connection (connect, upload GGA, receive RTCM), real-time RTCM 3.x decoding, and BLE RTK receiver
integration (Nordic UART Service scan/connect, NMEA parsing, RTCM forwarding) are implemented.
Offline map, mock location provider, TCP servers, and the data logger are not yet.

- Building: `cd android && JAVA_HOME="<path to a JDK>" ./gradlew assembleDebug` (needs
  `local.properties`, not committed, with `sdk.dir` pointing at your Android SDK). Targets
  `compileSdk`/`targetSdk` 36, `minSdk` 26.
- See [`CLAUDE.md`](./CLAUDE.md) for what's actually implemented vs. planned, and architecture
  notes for what's built so far.

---

## ?? Key Features

### ?? 1. BLE RTK Receiver Integration
- Connects seamlessly to Bluetooth Low Energy (BLE) GNSS receivers using standard Nordic UART Service (NUS).
- Continuously parses raw NMEA sentences (`$GNGGA`, `$GNRMC`, `$GNGST`) to extract latitude, longitude, altitude, ellipsoid height, HDOP/VDOP/PDOP, satellite count, and horizontal/vertical standard deviation.
- Bi-directional communication: Transmits RTCM 3.x correction frames directly to the connected receiver over BLE.

### ?? 2. NTRIP 2.0 Caster Bridge
- High-performance NTRIP Client supporting CORS networks (Default: `rtk.geodnet.com:2101`).
- **NTRIP Credentials Persistence**: Automatically stores Host, Port, Mountpoint, Username, Password, and GGA dispatch preferences across sessions.
- **Smart Phone Location GGA Fallback**: Automatically updates the NTRIP Caster with the Phone's location if no BLE RTK receiver is connected, ensuring immediate connection initialization.
- **GNSS Ephemeris Filtering**: Optional toggle to filter out heavy satellite ephemeris frames (`1019`, `1020`, `1041-1046`) before sending RTCM data over BLE, saving serial bandwidth for low-power rovers.

### ?? 3. RTCM 3.x Inspector & Latency Engine
- Real-time decoding of RTCM 3.x message types:
  - **Base Station ARP**: `1005` / `1006`
  - **MSM Observation Frames**: `107X` (GPS), `108X` (GLONASS), `109X` (Galileo), `110X` (QZSS), `111X` (SBAS), `112X` (BeiDou), `113X` (NavIC)
  - **Auxiliary Frames**: `1033` (Antenna/Receiver Descriptor), `1230` (GLONASS Code-Phase Biases), `1019-1046` (Satellite Ephemerides)
- **Sub-Millisecond Epoch Span & Latency Engine**: Calculates first message latency, last message latency, and sub-millisecond transmission epoch duration ($\Delta t_{\text{epoch}} = t_{\text{last}} - t_{\text{first}}$) with 20-bit MSM TOW modulo matching and clock-skew tolerance.
- Custom ordered breakdown chips matching professional surveyor standards.

### ??? 4. Offline Leaflet Map & Persistent Trajectory Tracking
- Embedded Leaflet map loaded completely offline via bundled asset files (`map.html`, `leaflet.js`, `leaflet.css`).
- High-contrast **Green Triangle** symbol for Base Station ARP.
- **Base Station & Baseline Vector Toggle**:
  - *Default Mode (OFF)*: Hides Base Station, auto-centers & zooms on Rover.
  - *Base Mode (ON)*: Renders Base Station, draws baseline vector, shows baseline distance in km, and zooms out to fit both Rover and Base with manual pan enabled.
- **Persistent Trajectory Storage**: Retains trajectory track points in a background service buffer across tab navigation, screen rotation, and page reloads.
- **Static segment auto-detection**: automatically detection the static segments using spatial grouping given a distance cutoff (default 5cm changes) and time duration (default >5s), save the mean and std, start time and end time, number of epochs.
 

### ?? 5. Android Mock Location & SW Maps TCP Server
- **Android Mock Location Provider**: Injects sub-centimeter RTK fixes directly into the Android OS (`LocationManager`) so all Android apps (Google Maps, OsmAnd, Surveying tools) use the high-precision fix.
- **NMEA TCP Server**: Broadcasts raw NMEA streams over a local TCP socket (`127.0.0.1:10110`) for direct integration with GIS surveying apps like SW Maps.
- **RTCM TCP Server**: Broadcasts raw NMEA streams over a local TCP socket (`127.0.0.1:10120`) for direct integration with GIS surveying apps like SW Maps.

### ?? 6. Dual Data Logger (Raw Binary & Android GNSS Raw)
1. **Raw Binary Stream Logger**:
   - Logs `$GEOD` (NTRIP RTCM) and (BLE Receiver NMEA/Raw) streams in raw binary format.
   - Folder structure: `logs/yyyy-MM-dd/` (Calculated in **GPS Time**).
   - Filename format: `yyyy-MM-dd-HH-mm-ss-<mountpoint>-base.log` and `yyyy-MM-dd-HH-mm-ss-<roverId>-rove.log`.
2. **Android GNSS Raw Measurement, Ephemeris and IMU Logger**:
   - Captures raw pseudorange, carrier phase, Doppler, C/$N_0$, accumulated delta range (ADR), hardware clock drift, and satellite navigation subframes (ephemerides).
   - Output format compatible with **Google GNSS Analysis Tool**, **RTKLIB**, and **RINEX Converters** (`yyyy-MM-dd-HH-mm-ss-raw-gnss.txt`).

3. **save the auto-detected segments**:
