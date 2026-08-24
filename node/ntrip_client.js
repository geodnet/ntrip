'use strict';

const net = require('net');

const fs = require('fs');
    
const userAgent = "ntrip client Nodejs/1.0.0";

/* please visit https://github.com/geodnet/GEODNET_RTK_SERVICE for more details about GEODNET RTK service */
let username = 'usr';
let password = 'pwd'; /* need to get your own usr/pwd */
let host = 'rtk.geodnet.com';
let port = 2101;
let mount = 'AUTO'; /* can be AUTO, AUTO_ITRF2020, AUTO_WGS84 */
let lat = 37.398583184829945;
let lon =-121.97869617705001;
let alt = 100.0;
let nsat = 20;
let hdop = 1.0;
let age = 0;
let staid = 0;
let data_dir = 'data';
let gga_interval = 5000; /* NMEA GGA upload frequency in ms */
let debug = true; /* print real-time per-message RTCM decode info; --debug=false to disable */

const BOOLEAN_FLAGS = new Set(['h', 'help', 'debug']);

/* print usage */
function printUsage() {
  console.log(`${userAgent}

Usage: node ntrip_client.js [options]

Options:
  --host=<host>       Ntrip caster hostname (default: ${host})
  --port=<port>       Ntrip caster port (default: ${port})
  --mount=<mount>     Mountpoint, e.g. AUTO, AUTO_ITRF2014, AUTO_ITRF2020, AUTO_WGS84 (default: ${mount})
  --user=<user>       GEODNET account username (required)
  --password=<pass>   GEODNET account password (required)
  --lat=<lat>         Receiver latitude in decimal degrees (default: ${lat})
  --lon=<lon>         Receiver longitude in decimal degrees (default: ${lon})
  --xyz=<x,y,z>       Receiver position as WGS84 ECEF meters, e.g. --xyz=-2694556.1,-4293026.5,3857756.8
                      (overrides --lat/--lon/--alt)
  --data_dir=<dir>    Folder to write correction logs under (default: ${data_dir})
  --gga_interval=<ms> NMEA GGA upload frequency in milliseconds; 0 disables GGA upload (default: ${gga_interval})
  --debug=false       Disable real-time per-message RTCM decode info (default: on)
  -h, --help          Show this help message and exit

Contact GEODNET to obtain a user account. See
https://github.com/geodnet/GEODNET_RTK_SERVICE for details.`);
}

/* parse argument */
function parseArgv() {

  const argvs = process.argv.splice(2);
  const args = {};
  for (let i = 0; i < argvs.length; i++) {
    let key = '';
    let val = '';
    if (argvs[i].startsWith('-')) {
      key = argvs[i].replace(/^-*/, '');
    }

    if (key === '') {
      continue;
    }

    const idx = key.indexOf('=');
    if (idx >= 0) {
      val = key.substring(idx + 1);
      key = key.substring(0, idx);
    } else if (BOOLEAN_FLAGS.has(key)) {
      val = 'true';
    } else {
      val = argvs[i + 1];
      i += 1;
    }

    args[key] = val;
  }

  if ('h' in args || 'help' in args) {
    printUsage();
    process.exit(0);
  }

  let xyzOverride = null;

  for (const key in args) {
    switch (key) {
      case 'host':
        host = args[key];
        break;
      case 'mount':
        mount = args[key];
        break;
      case 'port':
        port = Number(args[key]);
        break;
      case 'user':
        username = args[key];
        break;
      case 'password':
        password = args[key];
        break;
      case 'lat':
        lat = Number(args[key]);
        break;
      case 'lon':
        lon = Number(args[key]);
        break;
      case 'xyz': {
        const parts = args[key].split(',').map(Number);
        if (parts.length === 3 && parts.every((v) => !Number.isNaN(v))) {
          xyzOverride = parts;
        } else {
          console.error(`Invalid --xyz value "${args[key]}", expected x,y,z (WGS84 ECEF meters)`);
          process.exit(1);
        }
        break;
      }
      case 'data_dir':
        data_dir = args[key];
        break;
      case 'gga_interval':
        gga_interval = Number(args[key]);
        break;
      case 'debug':
        debug = args[key] !== 'false';
        break;
      default:
        break;
    }
  }

  if (xyzOverride) {
    const llh = ecefToLlh(xyzOverride[0], xyzOverride[1], xyzOverride[2]);
    lat = llh.lat;
    lon = llh.lon;
    alt = llh.height;
  }
}
/* generate NMEA GGA */
function generateGGA(latitude, longitude, altitude, numSatellites, hdop, age, staid) {
    const now = new Date();
    const hours = String(now.getUTCHours()).padStart(2, '0');
    const minutes = String(now.getUTCMinutes()).padStart(2, '0');
    const seconds = String(now.getUTCSeconds()).padStart(2, '0');
    const time = `${hours}${minutes}${seconds}.000`;
  
    const latDir = latitude >= 0 ? 'N' : 'S';
    const lonDir = longitude >= 0 ? 'E' : 'W';

    latitude = Math.abs(latitude);
    longitude = Math.abs(longitude);
    
    const latDD = Math.floor(latitude);
    const lonDD = Math.floor(longitude);

    const latMM = (latitude-latDD)*60.0;
    const lonMM = (longitude-lonDD)*60.0;

    const latDDstr = String(latDD).padStart(2,'0');
    const lonDDstr = String(lonDD).padStart(3,'0');
  
    const gga = `$GPGGA,${time},${latDDstr}${latMM.toFixed(4)},${latDir},${lonDDstr}${lonMM.toFixed(4)},${lonDir},1,${numSatellites},${hdop.toFixed(2)},${altitude.toFixed(2)},M,0.0,M,${age.toFixed(2)},${staid}`;
    const checksum = calculateChecksum(gga);
    return `${gga}*${checksum.toString(16).toUpperCase()}`;
}
  
  function calculateChecksum(sentence) {
    let checksum = 0;
    for (let i = 1; i < sentence.length; i++) {
      checksum ^= sentence.charCodeAt(i);
    }
    return checksum;
  }

/* RTCM3 CRC-24Q, poly 0x1864CFB, init 0 (as used by RTCM3/RTKLIB) */
function crc24q(buf) {
  let crc = 0;
  for (let i = 0; i < buf.length; i++) {
    crc ^= buf[i] << 16;
    for (let j = 0; j < 8; j++) {
      crc <<= 1;
      if (crc & 0x1000000) {
        crc ^= 0x1864CFB;
      }
    }
    crc &= 0xFFFFFF;
  }
  return crc;
}

/* MSB-first bit reader over a Buffer, for RTCM3 payload field extraction */
class BitReader {
  constructor(buf) {
    this.buf = buf;
    this.bitPos = 0;
  }

  readUnsigned(nbits) {
    let value = 0;
    for (let i = 0; i < nbits; i++) {
      const bytePos = this.bitPos >> 3;
      const bitInByte = 7 - (this.bitPos & 7);
      const bit = (this.buf[bytePos] >> bitInByte) & 1;
      value = value * 2 + bit;
      this.bitPos++;
    }
    return value;
  }

  readSigned(nbits) {
    const value = this.readUnsigned(nbits);
    const signBit = Math.pow(2, nbits - 1);
    return value >= signBit ? value - Math.pow(2, nbits) : value;
  }

  readString(nbytes) {
    let s = '';
    for (let i = 0; i < nbytes; i++) {
      s += String.fromCharCode(this.readUnsigned(8));
    }
    return s;
  }

  skip(nbits) {
    this.bitPos += nbits;
  }
}

/* WGS84 ECEF (m) -> geodetic lat/lon (deg) and ellipsoidal height (m) */
function ecefToLlh(x, y, z) {
  const a = 6378137.0;
  const f = 1 / 298.257223563;
  const e2 = f * (2 - f);
  const lon = Math.atan2(y, x);
  const p = Math.sqrt(x * x + y * y);
  let lat = Math.atan2(z, p * (1 - e2));
  let height = 0;
  for (let i = 0; i < 5; i++) {
    const sinLat = Math.sin(lat);
    const N = a / Math.sqrt(1 - e2 * sinLat * sinLat);
    height = p / Math.cos(lat) - N;
    lat = Math.atan2(z, p * (1 - (e2 * N) / (N + height)));
  }
  return { lat: (lat * 180) / Math.PI, lon: (lon * 180) / Math.PI, height };
}

/* geodetic lat/lon (deg) and ellipsoidal height (m) -> WGS84 ECEF (m) */
function llhToEcef(latDeg, lonDeg, height) {
  const a = 6378137.0;
  const f = 1 / 298.257223563;
  const e2 = f * (2 - f);
  const latRad = (latDeg * Math.PI) / 180;
  const lonRad = (lonDeg * Math.PI) / 180;
  const sinLat = Math.sin(latRad);
  const N = a / Math.sqrt(1 - e2 * sinLat * sinLat);
  return {
    x: (N + height) * Math.cos(latRad) * Math.cos(lonRad),
    y: (N + height) * Math.cos(latRad) * Math.sin(lonRad),
    z: (N * (1 - e2) + height) * Math.sin(latRad),
  };
}

/* decode 1005 (Stationary RTK Reference Station ARP) / 1006 (+ antenna height) */
function decodeMsg1005_1006(payload, msgType) {
  const br = new BitReader(payload);
  br.skip(12); /* message number */
  const staid = br.readUnsigned(12);
  const itrf = br.readUnsigned(6);
  br.skip(4); /* GPS/GLONASS/Galileo indicators + reference-station indicator */
  const x = br.readSigned(38) * 0.0001;
  br.skip(2); /* single receiver oscillator indicator + reserved */
  const y = br.readSigned(38) * 0.0001;
  br.skip(2); /* quarter cycle indicator */
  const z = br.readSigned(38) * 0.0001;
  let antHeight = null;
  if (msgType === 1006) {
    antHeight = br.readUnsigned(16) * 0.0001;
  }
  return { staid, itrf, x, y, z, antHeight };
}

/* decode 1033 (Receiver and Antenna Descriptors) */
function decodeMsg1033(payload) {
  const br = new BitReader(payload);
  br.skip(12); /* message number */
  const staid = br.readUnsigned(12);
  const n = br.readUnsigned(8);
  const antDescriptor = br.readString(n);
  const antSetupId = br.readUnsigned(8);
  const m = br.readUnsigned(8);
  const antSerial = br.readString(m);
  const n1 = br.readUnsigned(8);
  const recType = br.readString(n1);
  const n2 = br.readUnsigned(8);
  const recFirmware = br.readString(n2);
  const n3 = br.readUnsigned(8);
  const recSerial = br.readString(n3);
  return { staid, antDescriptor, antSetupId, antSerial, recType, recFirmware, recSerial };
}

/* MSM (Multiple Signal Message) signal mask bit -> RTCM/RINEX signal code,
   indexed by signal ID 1-32 (array index = ID-1); "" means undefined/reserved.
   Source: RTCM 10403.3 MSM signal ID tables (ref tables 3.5-91/96/99/102/105/108/108.3),
   as implemented in RTKLIB's rtcm3.c. */
const MSM_SIG_GPS = ['', '1C', '1P', '1W', '', '', '', '2C', '2P', '2W', '', '', '', '', '2S', '2L', '2X', '', '', '', '', '5I', '5Q', '5X', '', '', '', '', '', '1S', '1L', '1X'];
const MSM_SIG_GLO = ['', '1C', '1P', '', '', '', '', '2C', '2P', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', ''];
const MSM_SIG_GAL = ['', '1C', '1A', '1B', '1X', '1Z', '', '6C', '6A', '6B', '6X', '6Z', '', '7I', '7Q', '7X', '', '8I', '8Q', '8X', '', '5I', '5Q', '5X', '', '', '', '', '', '', '', ''];
const MSM_SIG_QZS = ['', '1C', '', '', '', '', '', '', '6S', '6L', '6X', '', '', '', '2S', '2L', '2X', '', '', '', '', '5I', '5Q', '5X', '', '', '', '', '', '1S', '1L', '1X'];
const MSM_SIG_SBS = ['', '1C', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '5I', '5Q', '5X', '', '', '', '', '', '', '', ''];
const MSM_SIG_CMP = ['', '2I', '2Q', '2X', '', '', '', '6I', '6Q', '6X', '', '', '', '7I', '7Q', '7X', '', '', '', '', '', '5D', '5P', '5X', '7D', '', '', '', '', '1D', '1P', '1X'];
const MSM_SIG_IRN = ['', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '5A', '', '', '', '', '', '', '', '', '', ''];

/* MSM types are <base>1..7 (e.g. 1071-1077 = GPS MSM1-7); base = floor(type/10) */
const MSM_SYSTEM_BY_BASE_FULL = {
  107: { name: 'GPS', sigTable: MSM_SIG_GPS },
  108: { name: 'GLONASS', sigTable: MSM_SIG_GLO },
  109: { name: 'Galileo', sigTable: MSM_SIG_GAL },
  110: { name: 'SBAS', sigTable: MSM_SIG_SBS },
  111: { name: 'QZSS', sigTable: MSM_SIG_QZS },
  112: { name: 'BeiDou', sigTable: MSM_SIG_CMP },
  113: { name: 'NavIC/IRNSS', sigTable: MSM_SIG_IRN },
};

function getMsmSystem(msgType) {
  const base = Math.floor(msgType / 10);
  const ordinal = msgType % 10;
  const sys = MSM_SYSTEM_BY_BASE_FULL[base];
  if (!sys || ordinal < 1 || ordinal > 7) {
    return null;
  }
  return { name: sys.name, sigTable: sys.sigTable, ordinal };
}

/* decode the common MSM header: station id, epoch time, satellite/signal masks */
function decodeMsmHeader(payload, msgType, sys) {
  const br = new BitReader(payload);
  br.skip(12); /* message number */
  const staid = br.readUnsigned(12);

  let epoch;
  if (sys.name === 'GLONASS') {
    const dow = br.readUnsigned(3);
    const todSec = br.readUnsigned(27) * 0.001;
    epoch = { dow, todSec };
  } else if (sys.name === 'BeiDou') {
    const towSec = br.readUnsigned(30) * 0.001 + 14.0; /* BDT -> GPST */
    epoch = { towSec };
  } else {
    const towSec = br.readUnsigned(30) * 0.001;
    epoch = { towSec };
  }

  const sync = br.readUnsigned(1);
  const iod = br.readUnsigned(3);
  br.skip(7); /* reserved */
  const clkSteering = br.readUnsigned(2);
  const extClock = br.readUnsigned(2);
  const smoothing = br.readUnsigned(1);
  const smoothInterval = br.readUnsigned(3);

  const satIds = [];
  for (let j = 1; j <= 64; j++) {
    if (br.readUnsigned(1)) {
      satIds.push(j);
    }
  }
  const sigIds = [];
  for (let j = 1; j <= 32; j++) {
    if (br.readUnsigned(1)) {
      sigIds.push(j);
    }
  }

  const sigNames = sigIds.map((id) => (sys.sigTable && sys.sigTable[id - 1]) || `#${id}`);

  return { staid, epoch, sync, iod, nsat: satIds.length, nsig: sigIds.length, sigNames };
}

/* build a compact one-line decoded-content summary for types the client
   understands in detail; returns '' for types with no extra detail to show */
function describeFrameDetail(msgType, frame, payloadLen) {
  const payload = frame.slice(3, 3 + payloadLen);
  try {
    if (msgType === 1005 || msgType === 1006) {
      const d = decodeMsg1005_1006(payload, msgType);
      const llh = ecefToLlh(d.x, d.y, d.z);
      const rover = llhToEcef(lat, lon, alt);
      const dx = d.x - rover.x, dy = d.y - rover.y, dz = d.z - rover.z;
      const baselineKm = Math.sqrt(dx * dx + dy * dy + dz * dz) / 1000;
      let s = `staid=${d.staid} xyz=(${d.x.toFixed(3)},${d.y.toFixed(3)},${d.z.toFixed(3)}) llh=(${llh.lat.toFixed(7)},${llh.lon.toFixed(7)},${llh.height.toFixed(3)}) base=${baselineKm.toFixed(3)}km`;
      if (msgType === 1006) {
        s += ` antHt=${d.antHeight.toFixed(4)}m`;
      }
      return s;
    }
    if (msgType === 1033) {
      const d = decodeMsg1033(payload);
      return `staid=${d.staid} ant="${d.antDescriptor}"(${d.antSetupId}) sn="${d.antSerial}" rx="${d.recType}" fw="${d.recFirmware}" rxsn="${d.recSerial}"`;
    }
    const sys = getMsmSystem(msgType);
    if (sys) {
      const h = decodeMsmHeader(payload, msgType, sys);
      const epochStr = h.epoch.dow !== undefined
        ? `dow=${h.epoch.dow} tod=${h.epoch.todSec.toFixed(3)}`
        : `tow=${h.epoch.towSec.toFixed(3)}`;
      return `staid=${h.staid} sys=${sys.name} ${epochStr} nsat=${h.nsat} nsig=${h.nsig} sig=[${h.sigNames.join(',')}]`;
    }
    return '';
  } catch (err) {
    return `(decode error: ${err.message})`;
  }
}

/* stats accumulated since the last periodic report, reset in reportStats() */
let stats = {
  bytesReceived: 0,
  bytesCrcFail: 0,
  msgsDecoded: 0,
  msgsCrcFail: 0,
  msgCounts: {},
};
let lastReportTime = Date.now();
let rtcmBuffer = Buffer.alloc(0);

/* pull complete RTCM3 frames out of rtcmBuffer, tolerating split/merged TCP chunks */
function processRtcmBuffer() {
  for (;;) {
    if (rtcmBuffer.length === 0) {
      return;
    }
    if (rtcmBuffer[0] !== 0xD3) {
      const idx = rtcmBuffer.indexOf(0xD3, 1);
      rtcmBuffer = idx >= 0 ? rtcmBuffer.slice(idx) : Buffer.alloc(0);
      continue;
    }
    if (rtcmBuffer.length < 3) {
      return; /* wait for the rest of the header */
    }
    if ((rtcmBuffer[1] & 0xFC) !== 0) {
      /* reserved bits must be 0; this 0xD3 is data, not a frame start */
      rtcmBuffer = rtcmBuffer.slice(1);
      continue;
    }
    const payloadLen = ((rtcmBuffer[1] & 0x03) << 8) | rtcmBuffer[2];
    const frameLen = payloadLen + 6; /* 3-byte header + payload + 3-byte CRC */
    if (rtcmBuffer.length < frameLen) {
      return; /* wait for the rest of the frame */
    }

    const frame = rtcmBuffer.slice(0, frameLen);
    rtcmBuffer = rtcmBuffer.slice(frameLen);
    handleRtcmFrame(frame, payloadLen);
  }
}

/* decode one RTCM3 frame's message type/subtype and update stats */
function handleRtcmFrame(frame, payloadLen) {
  const computedCrc = crc24q(frame.slice(0, 3 + payloadLen));
  const receivedCrc = frame.readUIntBE(3 + payloadLen, 3);
  const crcOk = computedCrc === receivedCrc;

  const msgType = (frame[3] << 4) | (frame[4] >> 4);
  let msgKey = String(msgType);
  if (msgType === 4076 && payloadLen >= 3) {
    /* IGS SSR (message 4076): 12-bit type, 3-bit version, 8-bit IGS sub-message number */
    const subType = ((frame[4] & 0x01) << 7) | (frame[5] >> 1);
    msgKey = `${msgType}.${subType}`;
  } else if (msgType >= 4070 && msgType <= 4095 && payloadLen >= 2) {
    /* other proprietary message ranges (e.g. u-blox 4072.x): 12-bit type + 4-bit subtype */
    const subType = frame[4] & 0x0F;
    msgKey = `${msgType}.${subType}`;
  }

  if (debug) {
    const now = new Date();
    const ts = `${pad2(now.getUTCHours())}:${pad2(now.getUTCMinutes())}:${pad2(now.getUTCSeconds())}.${String(now.getUTCMilliseconds()).padStart(3, '0')}`;
    const sizeStr = `${frame.length}B`.padStart(6);
    if (crcOk) {
      const detail = describeFrameDetail(msgType, frame, payloadLen);
      console.log(`[${ts}] ${msgKey.padEnd(9)} ${sizeStr}  OK${detail ? '  ' + detail : ''}`);
    } else {
      console.log(`[${ts}] ${msgKey.padEnd(9)} ${sizeStr}  FAIL  exp=0x${receivedCrc.toString(16)} calc=0x${computedCrc.toString(16)}`);
    }
  }

  if (!crcOk) {
    stats.msgsCrcFail += 1;
    stats.bytesCrcFail += frame.length;
    return;
  }

  stats.msgsDecoded += 1;
  stats.msgCounts[msgKey] = (stats.msgCounts[msgKey] || 0) + 1;
}

/* human-readable descriptions for common RTCM3 message types */
const RTCM_MSG_DESCRIPTIONS = {
  1001: 'GPS L1 RTK observables',
  1002: 'GPS L1 RTK observables (extended)',
  1003: 'GPS L1/L2 RTK observables',
  1004: 'GPS L1/L2 RTK observables (extended)',
  1005: 'Stationary RTK reference station ARP',
  1006: 'Stationary RTK reference station ARP with antenna height',
  1007: 'Antenna descriptor',
  1008: 'Antenna descriptor and serial number',
  1009: 'GLONASS L1 RTK observables',
  1010: 'GLONASS L1 RTK observables (extended)',
  1011: 'GLONASS L1/L2 RTK observables',
  1012: 'GLONASS L1/L2 RTK observables (extended)',
  1013: 'System parameters',
  1019: 'GPS ephemeris',
  1020: 'GLONASS ephemeris',
  1029: 'Unicode text string',
  1033: 'Receiver and antenna descriptors',
  1042: 'BeiDou ephemeris',
  1044: 'QZSS ephemeris',
  1045: 'Galileo F/NAV ephemeris',
  1046: 'Galileo I/NAV ephemeris',
  1230: 'GLONASS code-phase biases',
};

/* RTCM SSR message blocks: each GNSS gets 6 consecutive types
   (orbit, clock, code bias, combined orbit+clock, URA, high-rate clock) */
const SSR_KIND_BY_OFFSET = [
  'Orbit Correction',
  'Clock Correction',
  'Code Bias',
  'Combined Orbit and Clock Correction',
  'URA',
  'High Rate Clock Correction',
];
const SSR_SYSTEM_BLOCKS = [
  { base: 1057, system: 'GPS' },
  { base: 1063, system: 'GLONASS' },
  { base: 1240, system: 'Galileo' },
  { base: 1246, system: 'QZSS' },
  { base: 1252, system: 'SBAS' },
  { base: 1258, system: 'BeiDou' },
];
for (const { base, system } of SSR_SYSTEM_BLOCKS) {
  SSR_KIND_BY_OFFSET.forEach((kind, offset) => {
    RTCM_MSG_DESCRIPTIONS[base + offset] = `${system} SSR ${kind}`;
  });
}
/* SSR phase bias messages, added separately from the 6-message blocks above */
['GPS', 'GLONASS', 'Galileo', 'QZSS', 'SBAS', 'BeiDou'].forEach((system, offset) => {
  RTCM_MSG_DESCRIPTIONS[1265 + offset] = `${system} SSR Phase Bias`;
});

/* MSM message families: type = <base>1..7, e.g. 1071-1077 = GPS MSM1-7 */
const MSM_SYSTEM_BY_BASE = {
  107: 'GPS',
  108: 'GLONASS',
  109: 'Galileo',
  110: 'SBAS',
  111: 'QZSS',
  112: 'BeiDou',
  113: 'NavIC/IRNSS',
};

/* IGS SSR sub-type message numbers (IDF002) carried inside RTCM message 4076,
   per the IGS State Space Representation (SSR) format v1.00, Table 5.
   Each GNSS gets a block of 7 consecutive numbers: Orbit, Clock, Combined
   Orbit+Clock, High Rate Clock, Code Bias, Phase Bias, URA. */
const IGS_SSR_KIND_BY_OFFSET = [
  'Orbit Correction',
  'Clock Correction',
  'Combined Orbit and Clock Correction',
  'High Rate Clock Correction',
  'Code Bias',
  'Phase Bias',
  'URA',
];
const IGS_SSR_SYSTEM_BLOCKS = [
  { base: 21, system: 'GPS' },
  { base: 41, system: 'GLONASS' },
  { base: 61, system: 'Galileo' },
  { base: 81, system: 'QZSS' },
  { base: 101, system: 'BeiDou' },
  { base: 121, system: 'SBAS' },
];
const IGS_SSR_SUBTYPES = {
  201: 'Ionosphere VTEC Spherical Harmonics',
};
for (const { base, system } of IGS_SSR_SYSTEM_BLOCKS) {
  IGS_SSR_KIND_BY_OFFSET.forEach((kind, offset) => {
    IGS_SSR_SUBTYPES[base + offset] = `${system} SSR ${kind}`;
  });
}

/* msgKey is either "<type>" or "<type>.<subtype>" (see handleRtcmFrame) */
function describeMsgType(msgKey) {
  const [typeStr, subTypeStr] = String(msgKey).split('.');
  const msgType = parseInt(typeStr, 10);

  if (msgType === 4076 && subTypeStr !== undefined) {
    const subType = parseInt(subTypeStr, 10);
    return `IGS SSR ${IGS_SSR_SUBTYPES[subType] || `(unknown subtype ${subType})`}`;
  }
  if (RTCM_MSG_DESCRIPTIONS[msgType]) {
    return RTCM_MSG_DESCRIPTIONS[msgType];
  }
  const msmBase = Math.floor(msgType / 10);
  const msmOrdinal = msgType % 10;
  if (MSM_SYSTEM_BY_BASE[msmBase] && msmOrdinal >= 1 && msmOrdinal <= 7) {
    return `${MSM_SYSTEM_BY_BASE[msmBase]} MSM${msmOrdinal}`;
  }
  if (msgType >= 4001 && msgType <= 4095) {
    return 'Proprietary message';
  }
  return 'Unknown';
}

/* print and reset the periodic stats */
function reportStats() {
  const now = Date.now();
  const elapsedSec = (now - lastReportTime) / 1000;
  const bps = elapsedSec > 0 ? (stats.bytesReceived * 8) / elapsedSec : 0;

  console.log(`\n--- RTCM stats (last ${elapsedSec.toFixed(0)}s) ---`);
  console.log(`Bytes received: ${stats.bytesReceived} (${bps.toFixed(1)} bps)`);
  console.log(`Bytes with CRC failure: ${stats.bytesCrcFail}`);
  console.log(`Messages decoded: ${stats.msgsDecoded}`);
  console.log(`Messages with CRC failure: ${stats.msgsCrcFail}`);
  console.log('Message counts:');
  const types = Object.keys(stats.msgCounts).sort((a, b) => parseFloat(a) - parseFloat(b));
  if (types.length === 0) {
    console.log('  (none)');
  } else {
    for (const type of types) {
      console.log(`  ${type} (${describeMsgType(type)}): ${stats.msgCounts[type]}`);
    }
  }
  console.log('-----------------------------------\n');

  stats = { bytesReceived: 0, bytesCrcFail: 0, msgsDecoded: 0, msgsCrcFail: 0, msgCounts: {} };
  lastReportTime = now;
}

function pad2(n) {
  return String(n).padStart(2, '0');
}

/* yyyy-mm-dd-hh in UTC, identifies the hourly log folder/rotation */
function hourKeyOf(date) {
  return `${date.getUTCFullYear()}-${pad2(date.getUTCMonth() + 1)}-${pad2(date.getUTCDate())}-${pad2(date.getUTCHours())}`;
}

/* open a new log file under data_dir/<hourKey>/, named after the current UTC time */
function openLogWriter(date) {
  const hourKey = hourKeyOf(date);
  const dir = `${data_dir}/${hourKey}`;
  fs.mkdirSync(dir, { recursive: true });
  const fname = `${dir}/${hourKey}-${pad2(date.getUTCMinutes())}-${pad2(date.getUTCSeconds())}-${mount}.log`;
  return { stream: fs.createWriteStream(fname, "latin1"), hourKey };
}

/* main */

parseArgv();

let numofbyte = 0;
let is_header = 0;

let { stream: writers, hourKey: currentHourKey } = openLogWriter(new Date());

var client = new net.Socket()

client.connect(port, host, function() {
	console.log('Connected');
    const authorization = Buffer.from(
        username + ':' + password,
        'utf8'
      ).toString('base64');
      const data = `GET /${mount} HTTP/1.0\r\nUser-Agent: ${userAgent}\r\nAuthorization: Basic ${authorization}\r\n\r\n`;
      client.write(data);
	/* send GGA to server at the configured interval; gga_interval=0 disables GGA upload */
	if (gga_interval > 0) {
		const intervalId = setInterval(() => {
			const ggaSentence = generateGGA(lat, lon, alt, nsat, hdop, age, staid);
	        client.write(ggaSentence+'\r\n');
		  }, gga_interval);
	}
	/* report byte/message/CRC stats every 60s */
	lastReportTime = Date.now();
	setInterval(reportStats, 60000);
});

client.on('data', function(data) {
    numofbyte += data.length;
	if (!debug) {
		console.log('Received: '+data.length+'\tTotal: '+numofbyte);
	}

	stats.bytesReceived += data.length;
	rtcmBuffer = Buffer.concat([rtcmBuffer, data]);
	processRtcmBuffer();

	const now = new Date();
	const hourKey = hourKeyOf(now);
	if (hourKey !== currentHourKey) {
		writers.end();
		({ stream: writers, hourKey: currentHourKey } = openLogWriter(now));
	}

	writers.write(`$GEOD,${now.getTime()},${data.length},`);
	writers.write(data);
	writers.write('\r\n');

});

client.on('close', function() {
	console.log('Connection closed');
	writers.close();
});


setInterval(function() {
}, 2000);

   