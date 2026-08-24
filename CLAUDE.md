# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is a minimal example Ntrip client for accessing the GEODNET RTK correction service (see
https://github.com/geodnet/GEODNET_RTK_SERVICE). There is no build system, package manager,
or test suite — just plain Node.js scripts.

## Running

### `node/ntrip_client.js`

Connects to an Ntrip caster, streams RTCM corrections to a log file, and (by default) prints a
real-time decoded view of each message plus a periodic summary. No dependencies beyond Node's
built-in `net` and `fs`.

```
node node/ntrip_client.js [--host=...] [--port=...] [--mount=...] [--user=...] [--password=...]
                           [--lat=...] [--lon=...] [--xyz=x,y,z] [--data_dir=...]
                           [--gga_interval=ms] [--debug=false]
```

A real GEODNET account (`--user`/`--password`) is required to connect. Run with `--help` for the
full flag list and current defaults. Notable flags:

- `--xyz=x,y,z` sets the receiver position as WGS84 ECEF meters instead of `--lat`/`--lon`, and
  takes precedence over them if both are given.
- `--gga_interval=ms` controls how often the NMEA GGA position sentence is uploaded so GEODNET can
  select the nearest base station; `0` disables GGA upload entirely.
- `--debug` (on by default) prints one line per decoded RTCM message in real time, including
  message-specific detail (see Architecture below); `--debug=false` falls back to a plain
  `Received: <bytes> Total: <bytes>` line per TCP chunk instead.

Running the script writes correction data to `<data_dir>/<yyyy-mm-dd-hh>/<yyyy-mm-dd-hh-MM-SS>-<mount>.log`
(UTC), rotating to a new hourly folder/file automatically as the client runs. `data_dir` defaults
to `data`. These log files are runtime output, not source, and shouldn't be committed.

### `node/ntrip_sourcetable.js`

Fetches and prints an Ntrip caster's source table (available mountpoints):

```
node node/ntrip_sourcetable.js [--host=...] [--port=...] [--user=...] [--password=...]
```

Parses `NET`/`CAS`/`STR` lines per the Ntrip spec and prints networks/casters plus a
`console.table` of mountpoints. Credentials are optional here (many casters serve the source table
without auth).

## Architecture

### `ntrip_client.js`

Single-file script that does everything in one flow, top-to-bottom at module load (no `main()`):

1. `parseArgv()` — mutates module-level `let` config variables from `--key=value` / `--key value`
   / bare boolean (`-h`, `--help`, `--debug`) CLI args. A `--xyz=x,y,z` value is converted to
   lat/lon/alt (via `ecefToLlh`) after the rest of argv is parsed, so it always overrides
   `--lat`/`--lon` regardless of flag order.
2. `generateGGA()` / `calculateChecksum()` — build an NMEA `$GPGGA` sentence (with checksum) from
   the configured position, sent to the caster every `gga_interval` ms (skipped if `0`). The
   degrees-to-minutes conversion must multiply the fractional degree by 60 (not 100) — this was
   wrong in the original version and silently sent GEODNET an invalid position.
3. RTCM3 framing and decoding, driven by incoming socket data:
   - `processRtcmBuffer()` finds `0xD3` sync bytes and reassembles complete frames across
     TCP chunk boundaries, handing each complete frame to `handleRtcmFrame()`.
   - `handleRtcmFrame()` verifies CRC-24Q (`crc24q()`), extracts the 12-bit message type (plus a
     subtype for the proprietary 4070-4095 range, with special-cased bit layout for IGS SSR
     message 4076), and updates the periodic stats (`stats`).
   - When `debug` is on, each frame also gets a compact one-line real-time log via
     `describeFrameDetail()`, which understands 1005/1006 (station ID, ECEF XYZ, geodetic LLH,
     baseline distance to the configured receiver position, antenna height for 1006), 1033
     (antenna/receiver descriptor strings), MSM observation messages 1071-1137 (epoch time,
     satellite/signal counts, resolved signal names via per-constellation `MSM_SIG_*` tables), and
     ephemeris messages 1019/1020/1042/1044/1045/1046 (system, PRN, TOE as an absolute UTC
     timestamp, satellite health, and age relative to now — via `decodeEph*`/`decodeEphGalileo`).
     Ephemeris TOE reconstruction resolves each system's week number (handling the GPS/QZSS 10-bit
     week rollover, the Galileo week+1024 GPS-epoch offset, BeiDou's own 2006 epoch, and GLONASS's
     day-relative 15-minute index) and applies an approximate GPS-UTC/BDT-UTC leap-second offset —
     good enough for the debug display, not precision timing.
   - `BitReader` is the shared MSB-first bit-field reader used by all the message decoders above;
     bit layouts and MSM signal ID tables were cross-checked against RTKLIB's `rtcm3.c` rather
     than derived from spec text alone, since off-by-one bit widths there fail silently.
   - `reportStats()` runs every 60s, printing bytes received/CRC-failed, messages decoded/CRC-failed,
     and a per-message-type count with a human-readable description (`describeMsgType()`, covering
     named RTCM types, MSM families, standard RTCM SSR blocks, and IGS SSR sub-types per message
     4076 — see `doc/igs_ssr_v1.pdf` for the IGS SSR numbering this was built from).
4. Log writing: `openLogWriter()`/`hourKeyOf()` open a new file under a UTC-hour-keyed folder,
   rotating automatically when the wall-clock UTC hour changes. Each incoming chunk is framed as
   `$GEOD,<utc_ms>,<byte_length>,<raw_bytes>\r\n` — the raw bytes are written as-is (not hex/text
   encoded).

Since this is example/reference code, keep changes minimal and in the same plain, single-file
style unless asked for a larger restructuring.

### `ntrip_sourcetable.js`

Separate single-file script; sends `GET / HTTP/1.0` (with Basic Auth if credentials are given),
parses the sourcetable response body, and prints it. Independent of `ntrip_client.js`.

## Reference material

`doc/igs_ssr_v1.pdf` is the IGS State Space Representation (SSR) format spec — the source for the
IGS SSR sub-type numbering (message 4076) implemented in `ntrip_client.js`. If IGS SSR decoding
needs updating, check this doc rather than guessing the sub-type table from memory.
