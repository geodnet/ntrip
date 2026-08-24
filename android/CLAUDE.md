# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
subproject (`android/`). See the repo root `CLAUDE.md` for how this fits into the rest of the
repository, and `readme.md` in this folder for the full planned feature set — this file documents
what's actually implemented so far, not the plan.

## Status

Ntrip caster connection (connect, upload GGA, receive RTCM) plus a real-time RTCM 3.x inspector
(message decoding, live log, per-type counts). None of the other features from `readme.md` (BLE
receiver, offline map, mock location, TCP servers, dual logger) are implemented yet — build those
as separate follow-on work, each probably warranting its own package under `ntrip/`-style siblings
(e.g. `ble/`, `map/`, `logging/`).

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
  fields (host/port/mountpoint/credentials/lat/lon/alt/gga interval).
- `ntrip/GgaGenerator.kt` — builds the `$GPGGA` sentence + checksum, ported from the Node client's
  `generateGGA()`/`calculateChecksum()`. Uses the **corrected** degrees-to-minutes conversion
  (`× 60`, not the `× 100` bug the Node client originally had — see `node/CLAUDE.md`); don't
  reintroduce that mistake here.
- `ntrip/NtripClient.kt` — the actual caster connection: opens a `java.net.Socket`, sends the
  Ntrip `GET` request with Basic Auth, runs a GGA-upload coroutine on `config.ggaIntervalMs`
  (skipped entirely if `<= 0`), and a read loop that feeds every chunk into an `RtcmFrameParser`
  and tracks bytes into a `StateFlow<NtripState>`.
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
  `pendingStart` field.
- `data/SettingsRepository.kt` — Preferences DataStore-backed persistence for `NtripConfig`.
  **The password is stored in plaintext.** That's an explicit known gap, not an oversight — move
  to `EncryptedSharedPreferences` or Keystore-backed storage before this handles real credentials
  day-to-day.
- `MainActivity.kt` — single Compose entry point; requests `POST_NOTIFICATIONS` at runtime on
  API 33+ (required for the foreground-service notification to actually show).

## Known gaps / next steps

- No BLE integration, map, mock location provider, TCP servers, or data logger yet — see
  `readme.md` for what those should eventually do.
- RTCM stats are cumulative since connect, not reset periodically like the Node client's 60s
  report; the live log caps at 200 entries (oldest dropped).
- Only unit tests (`rtcm/` decoders) exist — no UI/instrumented tests.
- App icon uses the system default (`@android:drawable/sym_def_app_icon`) rather than a real
  launcher icon/adaptive icon set.
- Password storage is plaintext (see above).
