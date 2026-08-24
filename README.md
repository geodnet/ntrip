# ntrip

Open-source tools for working with [Ntrip](https://en.wikipedia.org/wiki/Networked_Transport_of_RTCM_via_Internet_Protocol)
casters and RTK correction streams, including [GEODNET](https://github.com/geodnet/GEODNET_RTK_SERVICE).
This repo is organized as a collection of independent projects, one per platform/use case.

## Projects

- [`node/`](./node) — Dependency-free Node.js Ntrip client (Windows/macOS/Linux): connects to a
  caster, uploads GGA, decodes and logs RTCM corrections in real time. Also includes a
  source-table lookup tool.
- [`android/`](./android) — Android app: connects to an Ntrip caster (GGA upload, RTCM receive),
  decodes RTCM in real time, and bridges corrections to a BLE RTK receiver over Nordic UART
  Service. Early stage — see `android/readme.md` for the full planned feature set and
  `android/CLAUDE.md` for what's actually implemented vs. planned.

Each project folder has its own README with setup/usage instructions, and its own CLAUDE.md with
architecture notes for anyone (human or Claude Code) working on that project's code.

## Using an Ntrip/GEODNET caster

Regardless of client, connecting to the GEODNET RTK service requires:

1. A user account and password — contact GEODNET to get one.
2. Uploading an NMEA GGA sentence with your approximate position.
3. GEODNET then streams corrections from the nearest base station in good status, based on
   GEODNET's station monitoring.
