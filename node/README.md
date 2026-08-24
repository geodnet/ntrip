# Node.js Ntrip Client

A minimal, dependency-free Ntrip client and source-table tool for the [GEODNET RTK correction
service](https://github.com/geodnet/GEODNET_RTK_SERVICE), running anywhere Node.js does
(Windows/macOS/Linux). No build step, no npm install — just `node <script>.js`.

## Requirements

- Node.js (any reasonably recent version — uses only the built-in `net` and `fs` modules)
- A GEODNET account (`--user`/`--password`) to connect to the correction stream

## `ntrip_client.js`

Connects to the caster, uploads a GGA position fix, and streams RTCM correction data to a
log file while printing a real-time decoded view of each message.

```
node ntrip_client.js --user=<your_user> --password=<your_password>
```

Run `node ntrip_client.js --help` for the full list of options (host/port/mountpoint, receiver
position via `--lat`/`--lon` or `--xyz`, GGA upload interval, log folder, debug output toggle).

## `ntrip_sourcetable.js`

Fetches and prints a caster's list of available mountpoints:

```
node ntrip_sourcetable.js --host=rtk.geodnet.com --port=2101
```

## More detail

See [`CLAUDE.md`](./CLAUDE.md) in this folder for an architecture walkthrough (RTCM3 framing,
message decoding, log rotation) if you're modifying the code.
